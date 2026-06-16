package com.search.engine.service;

import tools.jackson.databind.ObjectMapper;
import com.search.engine.client.openai.OpenAiClient;
import com.search.engine.client.weaviate.WeaviateClient;
import com.search.engine.model.PageResponse;
import com.search.engine.model.ProductDto;
import com.search.engine.model.ProductSuggestion;
import com.search.engine.model.ProductSyncResult;
import com.search.engine.model.SemanticStatusResult;
import com.search.engine.model.SuggestionResponse;
import org.jspecify.annotations.NonNull;
import com.search.engine.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {

	private static final Logger log = LoggerFactory.getLogger(ProductService.class);

	private final ProductRepository repository;
	private final WebClient webClient;
	private final String opensearchUrl;
	private final String productsIndex;
	private final WeaviateClient weaviateClient;
	private final OpenAiClient openAiClient;
	private final SearchIntentCatalog searchIntentCatalog;
	private final ObjectMapper mapper = new ObjectMapper();

	public ProductService(ProductRepository repository,
						  WebClient.Builder webClientBuilder,
						  @Value("${opensearch.url:http://localhost:9200}") String opensearchUrl,
						  @Value("${app.products.index:products}") String productsIndex,
						  WeaviateClient weaviateClient,
						  OpenAiClient openAiClient,
						  SearchIntentCatalog searchIntentCatalog) {
		this.repository = repository;
		this.webClient = webClientBuilder.baseUrl(opensearchUrl).build();
		this.opensearchUrl = opensearchUrl;
		this.productsIndex = productsIndex;
		this.weaviateClient = weaviateClient;
		this.openAiClient = openAiClient;
		this.searchIntentCatalog = searchIntentCatalog;
	}

	public Mono<Void> createProductTable() {
		return repository.createTableIfNotExists();
	}

	public Mono<Long> countProducts() {
		return repository.createTableIfNotExists()
				.then(repository.count());
	}

	public Mono<Void> saveProductOnly(ProductDto product) {
		return repository.save(product);
	}

	public Mono<Void> resetProductStores() {
		log.info("Resetting product table, OpenSearch index, and Weaviate class.");
		return repository.createTableIfNotExists()
				.then(repository.truncate())
				.then(deleteOpenSearchIndex())
				.then(weaviateClient.deleteClass("Product"));
	}

	public Mono<Void> waitForSearchStoreCounts(int expectedProducts, Duration timeout) {
		Instant deadline = Instant.now().plus(timeout);
		return waitForSearchStoreCounts(expectedProducts, deadline);
	}

	private Mono<Void> waitForSearchStoreCounts(int expectedProducts, Instant deadline) {
		return Mono.zip(openSearchCount(), weaviateClient.count("Product"))
				.flatMap(counts -> {
					long openSearchProducts = counts.getT1();
					long weaviateProducts = counts.getT2();
					if (openSearchProducts >= expectedProducts && weaviateProducts >= expectedProducts) {
						log.info("Search stores are ready. OpenSearch products={}, Weaviate products={}", openSearchProducts, weaviateProducts);
						return Mono.empty();
					}
					if (Instant.now().isAfter(deadline)) {
						return Mono.error(new IllegalStateException("Timed out waiting for search stores. OpenSearch products="
								+ openSearchProducts + ", Weaviate products=" + weaviateProducts + ", expected=" + expectedProducts));
					}
					log.info("Waiting for CDC indexing. OpenSearch products={}, Weaviate products={}, expected={}",
							openSearchProducts, weaviateProducts, expectedProducts);
					return Mono.delay(Duration.ofSeconds(5))
							.then(waitForSearchStoreCounts(expectedProducts, deadline));
				});
	}

	public Mono<PageResponse<ProductDto>> search(String q, int page, int size) {
		return lexicalSearch(q, page, size)
				.zipWith(semanticProducts(q, size).collectList())
				.map(tuple -> mergeSearchResults(tuple.getT1(), tuple.getT2(), page, size));
	}

	public Mono<PageResponse<ProductDto>> searchOpenSearch(String q, int page, int size) {
		return lexicalSearch(q, page, size);
	}

	public Mono<PageResponse<ProductDto>> searchAi(String q, int page, int size) {
		if (page > 0) {
			return Mono.just(new PageResponse<>(List.of(), page, size, 0));
		}
		return semanticProducts(q, size)
				.collectList()
				.map(products -> new PageResponse<>(products, page, size, products.size()));
	}

	private Mono<PageResponse<ProductDto>> lexicalSearch(String q, int page, int size) {
		int from = page * size;
		String expandedQuery = expandQuery(q);
		// Build a combined fuzzy + prefix query to improve typo tolerance and prefix matching.
		Map<@NonNull String, @NonNull Object> multiMatch = new HashMap<>();
		multiMatch.put("query", expandedQuery);
		multiMatch.put("fields", List.of("name^4", "description^2", "searchText"));
		multiMatch.put("type", "best_fields");
		multiMatch.put("fuzziness", "AUTO");

		Map<@NonNull String, @NonNull Object> matchPhrasePrefix = Map.of(
				"match_phrase_prefix", Map.of("name", Map.of("query", q, "boost", 2.0))
		);

		Map<@NonNull String, @NonNull Object> bool = new HashMap<>();
		bool.put("should", List.of(
				Map.of("multi_match", multiMatch),
				matchPhrasePrefix,
				Map.of("match_phrase_prefix", Map.of("description", Map.of("query", expandedQuery)))
		));
		bool.put("minimum_should_match", 1);

		Map<@NonNull String, @NonNull Object> query = new HashMap<>();
		query.put("query", Map.of("bool", bool));
		query.put("from", from);
		query.put("size", size);

		return webClient.post()
				.uri(uriBuilder -> uriBuilder.path("/" + productsIndex + "/_search").build())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(query)
				.retrieve()
				.bodyToMono(String.class)
				.map(body -> {
					List<ProductDto> items = new ArrayList<>();
					long total = 0;
					try {
						var root = mapper.readTree(body);
						var hits = root.path("hits");
						total = hits.path("total").path("value").asLong(0);
						for (var h : hits.path("hits")) {
							var src = h.path("_source");
							Long id = src.path("id").isMissingNode() ? null : src.path("id").asLong();
							String name = src.path("name").asText(null);
							String desc = src.path("description").asText(null);
							Double price = src.path("price").isMissingNode() ? null : src.path("price").asDouble();
							items.add(new ProductDto(id, name, desc, price));
						}
					} catch (Exception ignored) {}
					return new PageResponse<>(items, page, size, total);
				});
	}


	public Mono<PageResponse<ProductDto>> list(int page, int size) {
		return repository.createTableIfNotExists()
				.then(repository.count()
						.zipWith(repository.findPage(page, size).collectList())
						.map(tuple -> new PageResponse<>(tuple.getT2(), page, size, tuple.getT1())));
	}

	public Mono<SuggestionResponse> suggestions(String q, int size) {
		int max = Math.max(1, Math.min(size, 5));
		List<ProductSuggestion> intents = searchIntentCatalog.intentSuggestions(q, 2);
		int prefixLimit = Math.max(1, max - intents.size());
		return prefixSuggestions(q, prefixLimit)
				.zipWith(semanticSuggestions(q, max).collectList())
				.map(tuple -> {
					Map<String, ProductSuggestion> merged = new LinkedHashMap<>();
					for (ProductSuggestion suggestion : intents) {
						merged.put(suggestionKey(suggestion), suggestion);
					}
					for (ProductSuggestion suggestion : tuple.getT1()) {
						merged.put(suggestionKey(suggestion), suggestion);
					}
					for (ProductSuggestion suggestion : tuple.getT2()) {
						merged.putIfAbsent(suggestionKey(suggestion), suggestion);
					}
					return new SuggestionResponse(q, merged.values().stream().limit(max).toList());
				});
	}

	public Mono<ProductDto> get(Long id) {
		return repository.createTableIfNotExists()
				.then(repository.findById(id))
				.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found")));
	}

	public Mono<ProductDto> create(ProductDto input) {
		return repository.createTableIfNotExists()
				.then(resolveCreateProduct(input))
				.flatMap(product -> repository.save(product)
						.then(indexProduct(product))
						.thenReturn(product));
	}

	public Mono<ProductDto> update(Long id, ProductDto input) {
		return repository.createTableIfNotExists()
				.then(repository.existsById(id))
				.flatMap(exists -> {
					if (!exists) {
						return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
					}
					ProductDto product = new ProductDto(id, input.getName(), input.getDescription(), input.getPrice());
					return repository.save(product)
							.then(indexProduct(product))
							.thenReturn(product);
				});
	}

	public Mono<Void> delete(Long id) {
		return repository.createTableIfNotExists()
				.then(repository.deleteById(id))
				.flatMap(deleted -> {
					if (!deleted) {
						return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
					}
					return deleteFromOpenSearch(id)
							.then(weaviateClient.delete("Product", String.valueOf(id)));
				});
	}

	private Mono<ProductDto> resolveCreateProduct(ProductDto input) {
		if (input.getId() != null) {
			return Mono.just(input);
		}

		return repository.nextId()
				.map(id -> new ProductDto(id, input.getName(), input.getDescription(), input.getPrice()));
	}

	public Mono<Void> indexProduct(ProductDto product) {
		return indexProductStrict(product)
				.onErrorResume(e -> Mono.empty());
	}

	public Mono<Void> indexProductStrict(ProductDto product) {
		return upsertToOpenSearchStrict(product)
				.then(openAiClient.embed(productText(product)))
				.flatMap(vector -> upsertProductVectorStrict(product, vector));
	}

	public Mono<Void> indexProductFromCdc(ProductDto product) {
		return upsertToOpenSearchStrict(product)
				.then(upsertProductVectorIfAvailable(product));
	}

	public Mono<Void> upsertToOpenSearch(ProductDto p) {
		return upsertToOpenSearchStrict(p)
				.onErrorResume(e -> Mono.empty());
	}

	public Mono<Void> upsertToOpenSearchStrict(ProductDto p) {
		Map<@NonNull String, @NonNull Object> body = new HashMap<>();
		body.put("id", p.getId());
		body.put("name", safeText(p.getName()));
		body.put("description", safeText(p.getDescription()));
		body.put("searchText", productText(p));
		body.put("price", safePrice(p.getPrice()));

		return webClient.put()
				.uri("/" + productsIndex + "/_doc/" + p.getId() + "?refresh=true")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.retrieve()
				.bodyToMono(Void.class);
	}

	public Mono<Void> deleteFromOpenSearch(Long id) {
		return deleteFromOpenSearchStrict(id)
				.onErrorResume(e -> Mono.empty());
	}

	public Mono<Void> deleteProductFromSearchStoresStrict(Long id) {
		return deleteFromOpenSearchStrict(id)
				.then(weaviateClient.deleteStrict("Product", String.valueOf(id)));
	}

	private Mono<Void> deleteFromOpenSearchStrict(Long id) {
		return webClient.delete()
				.uri("/" + productsIndex + "/_doc/" + id + "?refresh=true")
				.retrieve()
				.bodyToMono(Void.class)
				.onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
	}

	private Mono<Void> deleteOpenSearchIndex() {
		return webClient.delete()
				.uri("/" + productsIndex)
				.retrieve()
				.bodyToMono(Void.class)
				.onErrorResume(e -> Mono.empty());
	}

	private Mono<Long> openSearchCount() {
		return webClient.get()
				.uri("/" + productsIndex + "/_count")
				.retrieve()
				.bodyToMono(String.class)
				.map(body -> {
					try {
						return mapper.readTree(body).path("count").asLong(0);
					} catch (Exception ignored) {
						return 0L;
					}
				})
				.onErrorReturn(0L);
	}

	public Mono<Void> reindexAll() {
		return syncProducts().then();
	}

	public Mono<ProductSyncResult> syncProducts() {
		return repository.createTableIfNotExists()
				.then(Mono.zip(repository.count(), weaviateClient.count("Product"), embeddingDimensions()))
				.flatMap(initial -> {
					long databaseProducts = initial.getT1();
					long weaviateBefore = initial.getT2();
					int dimensions = initial.getT3();
					boolean embeddingsWorking = dimensions > 0;

					if (!embeddingsWorking) {
						String message = openAiClient.isConfigured()
								? "OpenAI key is configured, but embedding request did not return a vector."
								: "OPENAI_API_KEY is not configured in the running Spring Boot process.";
						return weaviateClient.count("Product")
								.map(weaviateAfter -> new ProductSyncResult(
										databaseProducts,
										weaviateBefore,
										weaviateAfter,
										0,
										(int) databaseProducts,
										openAiClient.isConfigured(),
										dimensions,
										false,
										List.of(message)
								));
					}

					return resetSearchStores()
							.thenMany(repository.findAll()
							.flatMapSequential(product -> syncProductToSearchStores(product)
									.thenReturn("")
									.onErrorResume(error -> Mono.just(syncError(product, error))), 4)
							.filter(error -> !error.isBlank()))
							.collectList()
							.zipWith(weaviateClient.count("Product"))
							.map(done -> {
								List<String> errors = done.getT1();
								long weaviateAfter = done.getT2();
								int failed = errors.size();
								return new ProductSyncResult(
										databaseProducts,
										weaviateBefore,
										weaviateAfter,
										(int) databaseProducts - failed,
										failed,
										openAiClient.isConfigured(),
										dimensions,
										true,
										errors.stream().limit(20).toList()
								);
							});
				});
	}

	private Mono<Void> resetSearchStores() {
		return deleteOpenSearchIndex()
				.then(weaviateClient.deleteClass("Product"));
	}

	public Mono<SemanticStatusResult> semanticStatus() {
		return embeddingDimensions().zipWith(weaviateClient.count("Product"))
				.map(tuple -> new SemanticStatusResult(
						openAiClient.isConfigured(),
						tuple.getT1(),
						tuple.getT1() > 0,
						tuple.getT2(),
						tuple.getT1() > 0 && tuple.getT2() > 0
				));
	}

	private Mono<Integer> embeddingDimensions() {
		if (!openAiClient.isConfigured()) {
			return Mono.just(0);
		}
		return openAiClient.embed("semantic search health check")
				.map(List::size)
				.onErrorReturn(0);
	}

	private Mono<Void> syncProductToSearchStores(ProductDto product) {
		return upsertToOpenSearchStrict(product)
				.then(openAiClient.embed(productText(product)))
				.flatMap(vector -> upsertProductVectorStrict(product, vector));
	}

	private Mono<Void> upsertProductVectorStrict(ProductDto product, List<Float> vector) {
		if (vector.isEmpty()) {
			return Mono.error(new IllegalStateException("OpenAI returned an empty embedding vector"));
		}
		return weaviateClient.upsertStrict("Product", String.valueOf(product.getId()), productProperties(product), vector);
	}

	private Mono<Void> upsertProductVectorIfAvailable(ProductDto product) {
		if (!openAiClient.isConfigured()) {
			log.warn("Skipping vector sync for product {} because OPENAI_API_KEY is not configured.", product.getId());
			return Mono.empty();
		}
		return openAiClient.embed(productText(product))
				.flatMap(vector -> {
					if (vector.isEmpty()) {
						log.warn("Skipping vector sync for product {} because OpenAI returned an empty embedding vector.", product.getId());
						return Mono.empty();
					}
					return weaviateClient.upsertStrict("Product", String.valueOf(product.getId()), productProperties(product), vector);
				})
				.onErrorResume(error -> {
					log.warn("Skipping vector sync for product {} after embedding/vector error: {}", product.getId(), error.getMessage());
					return Mono.empty();
				});
	}

	private Map<@NonNull String, @NonNull Object> productProperties(ProductDto product) {
		Map<@NonNull String, @NonNull Object> props = new HashMap<>();
		props.put("name", safeText(product.getName()));
		props.put("description", safeText(product.getDescription()));
		props.put("searchText", productText(product));
		props.put("price", safePrice(product.getPrice()));
		return props;
	}

	private String safeText(String value) {
		return value == null ? "" : value;
	}

	private Double safePrice(Double value) {
		return value == null ? 0.0 : value;
	}

	private String syncError(ProductDto product, Throwable error) {
		String productId = product.getId() == null ? "unknown" : String.valueOf(product.getId());
		String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
		if (message.length() > 160) {
			message = message.substring(0, 160);
		}
		return "Product " + productId + ": " + message;
	}

	private Mono<List<ProductSuggestion>> prefixSuggestions(String q, int size) {
		return lexicalSearch(q, 0, size)
				.map(page -> page.getItems().stream()
						.map(product -> new ProductSuggestion("PREFIX", product.getId(), product.getName()))
						.toList())
				.onErrorReturn(List.of());
	}

	private Flux<ProductSuggestion> semanticSuggestions(String q, int size) {
		return semanticProducts(q, Math.max(size, 5))
				.map(product -> new ProductSuggestion("SEMANTIC", product.getId(), product.getName()))
				.onErrorResume(e -> Flux.empty());
	}

	private Flux<ProductDto> semanticProducts(String q, int size) {
		return openAiClient.embed(expandQuery(q))
				.flatMapMany(vector -> weaviateClient.searchNearVector("Product", vector, size))
				.onErrorResume(e -> Flux.empty());
	}

	private PageResponse<ProductDto> mergeSearchResults(PageResponse<ProductDto> lexical,
														List<ProductDto> semantic,
														int page,
														int size) {
		Map<String, ProductDto> merged = new LinkedHashMap<>();
		for (ProductDto product : lexical.getItems()) {
			merged.put(productKey(product), product);
		}
		for (ProductDto product : semantic) {
			merged.putIfAbsent(productKey(product), product);
		}
		List<ProductDto> items = merged.values().stream().limit(size).toList();
		long total = Math.max(lexical.getTotalElements(), items.size());
		return new PageResponse<>(items, page, size, total);
	}

	private String productKey(ProductDto product) {
		if (product.getId() != null) {
			return "id:" + product.getId();
		}
		return "name:" + String.valueOf(product.getName()).toLowerCase();
	}

	private String expandQuery(String q) {
		return searchIntentCatalog.expandQuery(q);
	}

	private String productText(ProductDto product) {
		String base = String.join(" ",
				product.getName() == null ? "" : product.getName(),
				product.getDescription() == null ? "" : product.getDescription());
		return base + " " + searchIntentCatalog.productAliases(base);
	}

	private String suggestionKey(ProductSuggestion suggestion) {
		if (suggestion.getProductId() != null) {
			return "id:" + suggestion.getProductId();
		}
		return "text:" + String.valueOf(suggestion.getText()).toLowerCase();
	}

}
