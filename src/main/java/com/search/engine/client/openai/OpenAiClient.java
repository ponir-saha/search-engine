package com.search.engine.client.openai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
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
		return apiKey != null && !apiKey.isBlank();
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
				.bodyToMono(Map.class)
				.map(resp -> {
					try {
						var data = (List<?>) resp.get("data");
						if (data == null || data.isEmpty()) return new ArrayList<Float>();
                        var first = (Map<?, ?>) data.get(0);
                        var embedding = (List<?>) first.get("embedding");
                        List<Float> out = new ArrayList<>();
                        for (Object o : embedding) {
                            if (o instanceof Number) out.add(((Number) o).floatValue());
                        }
                        return out;
					} catch (Exception e) {
						return new ArrayList<Float>();
					}
				});
	}
}
