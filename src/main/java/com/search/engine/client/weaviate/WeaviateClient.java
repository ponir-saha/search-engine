package com.search.engine.client.weaviate;

import com.search.engine.model.ProductDto;
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
	public Mono<Void> upsert(String index, String id, Map<String, Object> doc) {
		return upsert(index, id, doc, List.of());
	}

	public Mono<Void> upsert(String index, String id, Map<String, Object> doc, List<Float> vector) {
		return upsertRequest(index, id, doc, vector)
				.onErrorResume(e -> Mono.empty());
	}

	public Mono<Void> upsertStrict(String index, String id, Map<String, Object> doc, List<Float> vector) {
		return upsertRequest(index, id, doc, vector);
	}

	private Mono<Void> upsertRequest(String index, String id, Map<String, Object> doc, List<Float> vector) {
		// Weaviate create object: POST /v1/objects
		// doc will be the properties map; index is mapped to class name in Weaviate.
		Map<String, Object> properties = new HashMap<>(doc);
		properties.put("productId", id);

        Map<String, Object> body = new HashMap<>();
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

	public Flux<ProductDto> searchNearVector(String index, List<Float> vector, int limit) {
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

        Map<String, Object> body = Map.of(
                "query", graphQl,
                "variables", Map.of("vector", vector, "limit", Math.max(limit, 1))
        );

        return client.post()
                .uri("/v1/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
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
				.bodyToMono(Map.class)
				.map(response -> {
					Object total = response.get("totalResults");
					if (total instanceof Number number) {
						return number.longValue();
					}
					return 0L;
				})
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

    private List<ProductDto> readProducts(Map<?, ?> response) {
        List<ProductDto> products = new ArrayList<>();
        try {
            Map<?, ?> data = (Map<?, ?>) response.get("data");
            Map<?, ?> get = (Map<?, ?>) data.get("Get");
            List<?> rows = (List<?>) get.get("Product");
            for (Object rowObject : rows) {
                Map<?, ?> row = (Map<?, ?>) rowObject;
                Long id = null;
                Object productId = row.get("productId");
                if (productId != null) {
                    id = Long.valueOf(String.valueOf(productId));
                }
                String name = row.get("name") == null ? null : String.valueOf(row.get("name"));
                String description = row.get("description") == null ? null : String.valueOf(row.get("description"));
                Double price = row.get("price") instanceof Number number ? number.doubleValue() : null;
                products.add(new ProductDto(id, name, description, price));
            }
        } catch (Exception ignored) {
        }
        return products;
    }
}
