package com.search.engine.client.weaviate;

import com.search.engine.model.ProductDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WeaviateClient {

    private final WebClient client;
	private final Set<String> ensuredClasses = ConcurrentHashMap.newKeySet();

    public WeaviateClient(WebClient.Builder builder,
                          @Value("${vectordb.url:http://localhost:8085}") String vectordbUrl) {
        this.client = builder.baseUrl(vectordbUrl).build();
    }

    // Placeholder: create or upsert object
	public Mono<Void> upsert(String index, String id, Map< String,  Object> doc) {
		return upsert(index, id, doc, List.of());
	}

	public Mono<Void> upsert(String index, String id, Map< String,  Object> doc, List< Float> vector) {
		return upsertRequest(index, id, doc, vector)
				.onErrorResume(e -> Mono.empty());
	}

	public Mono<Void> upsertStrict(String index, String id, Map< String,  Object> doc, List< Float> vector) {
		return upsertRequest(index, id, doc, vector);
	}

	private Mono<Void> upsertRequest(String index, String id, Map< String,  Object> doc, List< Float> vector) {
		// Weaviate create object: POST /v1/objects
		// doc will be the properties map; index is mapped to class name in Weaviate.
		Map< String,  Object> properties = new HashMap<>(doc);
		properties.put("productId", id);

        Map< String,  Object> body = new HashMap<>();
		body.put("id", objectId(index, id));
        body.put("class", index);
        body.put("properties", properties);
        if (!vector.isEmpty()) {
            body.put("vector", vector);
        }

		return ensureProductClass(index)
				.then(deleteStrict(index, id))
				.then(client.post()
						.uri("/v1/objects")
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(body)
						.retrieve()
						.bodyToMono(Void.class));
	}

	public Mono<Void> ensureProductClass(String index) {
		if (ensuredClasses.contains(index)) {
			return Mono.empty();
		}

		Map< String,  Object> body = Map.of(
				"class", index,
				"vectorizer", "none",
				"properties", List.of(
						Map.of("name", "productId", "dataType", List.of("text")),
						Map.of("name", "name", "dataType", List.of("text")),
						Map.of("name", "description", "dataType", List.of("text")),
						Map.of("name", "searchText", "dataType", List.of("text")),
						Map.of("name", "price", "dataType", List.of("number"))
				)
		);

		return client.post()
				.uri("/v1/schema")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.retrieve()
				.bodyToMono(Void.class)
				.onErrorResume(WebClientResponseException.class, e -> {
					int status = e.getStatusCode().value();
					if (status == 409 || status == 422) {
						return Mono.empty();
					}
					return Mono.error(e);
				})
				.doOnSuccess(ignored -> ensuredClasses.add(index));
	}

	public Flux<ProductDto> searchNearVector(String index, List< Float> vector, int limit) {
		if (vector.isEmpty()) {
			return Flux.empty();
		}

		int safeLimit = Math.max(limit, 1);
        String graphQl = """
                query Search($vector: [Float]!) {
                  Get {
                    Product(
                      nearVector: {vector: $vector}
                      limit: %d
                    ) {
                      productId
                      name
                      description
                      price
                    }
                  }
                }
                """.formatted(safeLimit);

        Map< String,  Object> body = Map.of(
                "query", graphQl,
                "variables", Map.of("vector", vector)
        );

		return client.post()
				.uri("/v1/graphql")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.retrieve()
				.bodyToMono(ProductGraphQlResponse.class)
				.flatMapMany(response -> Flux.fromIterable(readProducts(response)))
				.onErrorResume(e -> Flux.empty());
	}

	public Mono<Long> count(String index) {
		String graphQl = """
				query CountProducts {
				  Aggregate {
				    Product {
				      meta {
				        count
				      }
				    }
				  }
				}
				""";

		return client.post()
				.uri("/v1/graphql")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("query", graphQl))
				.retrieve()
				.bodyToMono(ProductAggregateGraphQlResponse.class)
				.map(this::readProductCount)
				.onErrorReturn(0L);
	}

	public Mono<Void> delete(String index, String id) {
		return deleteStrict(index, id)
                .onErrorResume(e -> Mono.empty());
    }

	public Mono<Void> deleteStrict(String index, String id) {
		return client.delete()
				.uri("/v1/objects/" + index + "/" + objectId(index, id))
                .retrieve()
                .bodyToMono(Void.class)
				.onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }

	public Mono<Void> deleteClass(String index) {
		ensuredClasses.remove(index);
		return client.delete()
				.uri("/v1/schema/" + index)
				.retrieve()
				.bodyToMono(Void.class)
				.onErrorResume(e -> Mono.empty());
	}

    private String objectId(String index, String id) {
        return UUID.nameUUIDFromBytes((index + ":" + id).getBytes(StandardCharsets.UTF_8)).toString();
    }

	private List< ProductDto> readProducts(ProductGraphQlResponse response) {

        List< ProductDto> products = new ArrayList<>();
		if (response == null || response.getData() == null || response.getData().getGet() == null) {
			return products;
		}
		for (WeaviateProductRow row : response.getData().getGet().getProduct()) {
			if (row == null || row.getProductId() == null || row.getProductId().isBlank()) {
				continue;
			}
			Long id = null;
			id = Long.valueOf(row.getProductId());
            assert id != null;
            products.add(new ProductDto(id, row.getName(), row.getDescription(), row.getPrice()));
		}
		return products;
	}

	private long readProductCount(ProductAggregateGraphQlResponse response) {
		try {
			List<ProductAggregateRow> rows = response.getData().getAggregate().getProduct();
			if (rows.isEmpty()) {
				return 0L;
			}
			return rows.getFirst().getMeta().getCount();
		} catch (Exception e) {
			return 0L;
		}
	}

	@Data
	@NoArgsConstructor
	private static class ProductGraphQlResponse {
		private ProductGraphQlData data;
	}

	@Data
	@NoArgsConstructor
	private static class ProductGraphQlData {
		@JsonProperty("Get")
		private ProductGraphQlGet get;

    }

	@Data
	@NoArgsConstructor
	private static class ProductAggregateGraphQlResponse {
		private ProductAggregateGraphQlData data;
	}

	@Data
	@NoArgsConstructor
	private static class ProductAggregateGraphQlData {
		@JsonProperty("Aggregate")
		private ProductAggregateGraphQlAggregate aggregate;
	}

	@Data
	@NoArgsConstructor
	private static class ProductAggregateGraphQlAggregate {
		@JsonProperty("Product")
		private List<ProductAggregateRow> product = new ArrayList<>();
	}

	@Data
	@NoArgsConstructor
	private static class ProductAggregateRow {
		private ProductAggregateMeta meta = new ProductAggregateMeta();
	}

	@Data
	@NoArgsConstructor
	private static class ProductAggregateMeta {
		private long count;
	}

	@Data
	@NoArgsConstructor
	private static class ProductGraphQlGet {
		@JsonProperty("Product")
		private List< WeaviateProductRow> product = new ArrayList<>();

    }

	@Data
	@NoArgsConstructor
	private static class WeaviateProductRow {
		private String productId;
		private String name;
		private String description;
		private Double price;
	}
}
