package com.search.engine.client.openai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiClient {

    private final WebClient webClient;
    private final String apiKey;

	public OpenAiClient(WebClient.Builder builder, @Value("${openai.api-key:}") String apiKey) {
		this.webClient = builder.baseUrl("https://api.openai.com").build();
		this.apiKey = apiKey;
	}

	public boolean isConfigured() {
		return !apiKey.isBlank();
	}

	// Minimal embedding call. If OPENAI API key is not provided, returns empty Mono.
	public Mono<List<Float>> embed(String text) {
		if (!isConfigured()) {
			return Mono.just(new ArrayList<>());
		}

        var payload = Map.of(
                "model", "text-embedding-3-small",
                "input", text
        );

		return webClient.post()
				.uri("/v1/embeddings")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(payload)
				.retrieve()
				.onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
						.defaultIfEmpty("OpenAI embeddings request failed")
						.flatMap(body -> Mono.error(new IllegalStateException(body))))
				.bodyToMono(EmbeddingResponse.class)
				.map(resp -> {
					try {
						if (resp.getData().isEmpty()) return new ArrayList<>();
						var embedding = resp.getData().getFirst().getEmbedding();
						List<Float> out = new ArrayList<>();
						for (Number o : embedding) {
                            out.add(o.floatValue());
                        }
                        return out;
					} catch (Exception e) {
						return new ArrayList<>();
					}
				});
	}

	@Data
	@NoArgsConstructor
	private static class EmbeddingResponse {
		private List<EmbeddingData> data = new ArrayList<>();
	}

	@Data
	@NoArgsConstructor
	private static class EmbeddingData {
		private List<Double> embedding = new ArrayList<>();
	}
}
