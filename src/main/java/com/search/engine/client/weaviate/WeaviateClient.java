package com.search.engine.client.weaviate;

import com.search.engine.model.ProductDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class WeaviateClient {

    private final WebClient client;

    public WeaviateClient(WebClient.Builder builder,
                          @Value("${vectordb.url:http://localhost:8085}") String vectordbUrl) {
        this.client = builder.baseUrl(vectordbUrl).build();
    }

    // Placeholder: create or upsert object
	public Mono<Void> upsert(String index, String id, Map<@NonNull String, @NonNull Object> doc) {
		return upsert(index, id, doc, List.of());
	}

	public Mono<Void> upsert(String index, String id, Map<@NonNull String, @NonNull Object> doc, List<@NonNull Float> vector) {
		return upsertRequest(index, id, doc, vector)
				.onErrorResume(e -> Mono.empty());
	}

	public Mono<Void> upsertStrict(String index, String id, Map<@NonNull String, @NonNull Object> doc, List<@NonNull Float> vector) {
		return upsertRequest(index, id, doc, vector);
	}

	private Mono<Void> upsertRequest(String index, String id, Map<@NonNull String, @NonNull Object> doc, List<@NonNull Float> vector) {
		// Weaviate create object: POST /v1/objects
		// doc will be the properties map; index is mapped to class name in Weaviate.
		Map<@NonNull String, @NonNull Object> properties = new HashMap<>(doc);
		properties.put("productId", id);

        Map<@NonNull String, @NonNull Object> body = new HashMap<>();
        body.put("class", index);
        body.put("properties", properties);
        if (vector != null && !vector.isEmpty()) {
            body.put("vector", vector);
        }

		return client.put()
				.uri("/v1/objects/" + index + "/" + objectId(index, id))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.retrieve()
				.bodyToMono(Void.class);
	}

	public Flux<ProductDto> searchNearVector(String index, List<@NonNull Float> vector, int limit) {
		if (vector == null || vector.isEmpty()) {
			return Flux.empty();
		}

        String graphQl = """
                query Search($vector: [Float]!, $limit: Int!) {
                  Get {
                    Product(
                      nearVector: {vector: $vector}
                      limit: $limit
                    ) {
                      productId
                      name
                      description
                      price
                    }
                  }
                }
                """;

        Map<@NonNull String, @NonNull Object> body = Map.of(
                "query", graphQl,
                "variables", Map.of("vector", vector, "limit", Math.max(limit, 1))
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
		return client.get()
				.uri(uriBuilder -> uriBuilder.path("/v1/objects")
						.queryParam("class", index)
						.queryParam("limit", 1)
						.build())
				.retrieve()
				.bodyToMono(ObjectListResponse.class)
				.map(ObjectListResponse::getTotalResults)
				.onErrorReturn(0L);
	}

	public Mono<Void> delete(String index, String id) {
		return client.delete()
				.uri("/v1/objects/" + index + "/" + objectId(index, id))
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(e -> Mono.empty());
    }

    private String objectId(String index, String id) {
        return UUID.nameUUIDFromBytes((index + ":" + id).getBytes(StandardCharsets.UTF_8)).toString();
    }

	private List<@NonNull ProductDto> readProducts(ProductGraphQlResponse response) {
		if (response.getData() == null || response.getData().getGet() == null) {
			return List.of();
		}

		List<@NonNull ProductDto> products = new ArrayList<>();
		for (WeaviateProductRow row : response.getData().getGet().getProduct()) {
			Long id = null;
			if (row.getProductId() != null && !row.getProductId().isBlank()) {
				id = Long.valueOf(row.getProductId());
			}
			products.add(new ProductDto(id, row.getName(), row.getDescription(), row.getPrice()));
		}
		return products;
	}

	@Data
	@NoArgsConstructor
	private static class ObjectListResponse {
		private long totalResults;
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

		public ProductGraphQlGet getGet() {
			return get;
		}

		public void setGet(ProductGraphQlGet get) {
			this.get = get;
		}
	}

	@Data
	@NoArgsConstructor
	private static class ProductGraphQlGet {
		@JsonProperty("Product")
		private List<@NonNull WeaviateProductRow> product = new ArrayList<>();

		public List<@NonNull WeaviateProductRow> getProduct() {
			return product;
		}

		public void setProduct(List<@NonNull WeaviateProductRow> product) {
			this.product = product == null ? new ArrayList<>() : product;
		}
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
