package com.search.engine.client.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.search.engine.service.AiObservabilityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiClient {

	private static final String EMBEDDING_MODEL = "text-embedding-3-small";

    private final WebClient webClient;
    private final String apiKey;
	private final AiObservabilityService observabilityService;

	public OpenAiClient(WebClient.Builder builder,
						@Value("${openai.api-key:}") String apiKey,
						AiObservabilityService observabilityService) {
		this.webClient = builder.baseUrl("https://api.openai.com").build();
		this.apiKey = apiKey;
		this.observabilityService = observabilityService;
	}

	public boolean isConfigured() {
		return !apiKey.isBlank();
	}

	// Minimal embedding call. If OPENAI API key is not provided, returns empty Mono.
	public Mono<List<Float>> embed(String text) {
		if (!isConfigured()) {
			observabilityService.recordOpenAiEmbedding(EMBEDDING_MODEL, text, Duration.ZERO, 0, 0, false,
					new IllegalStateException("OpenAI API key is not configured"));
			return Mono.just(new ArrayList<>());
		}

        var payload = Map.of(
                "model", EMBEDDING_MODEL,
                "input", text
        );
		Instant startedAt = Instant.now();

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
						observabilityService.recordOpenAiEmbedding(EMBEDDING_MODEL, text, Duration.between(startedAt, Instant.now()),
								resp.getUsage().getPromptTokens(), resp.getUsage().getTotalTokens(), true, null);
						if (resp.getData().isEmpty()) {
							return new ArrayList<Float>();
						}
						var embedding = resp.getData().getFirst().getEmbedding();
						List<Float> out = new ArrayList<>();
						for (Number o : embedding) {
                            out.add(o.floatValue());
                        }
                        return out;
					} catch (Exception e) {
						observabilityService.recordOpenAiEmbedding(EMBEDDING_MODEL, text, Duration.between(startedAt, Instant.now()),
								0, 0, false, e);
						return new ArrayList<Float>();
					}
				})
				.doOnError(error -> observabilityService.recordOpenAiEmbedding(EMBEDDING_MODEL, text,
						Duration.between(startedAt, Instant.now()), 0, 0, false, error));
	}

	@Data
	@NoArgsConstructor
	private static class EmbeddingResponse {
		private List<EmbeddingData> data = new ArrayList<>();
		private EmbeddingUsage usage = new EmbeddingUsage();
	}

	@Data
	@NoArgsConstructor
	private static class EmbeddingData {
		private List<Double> embedding = new ArrayList<>();
	}

	@Data
	@NoArgsConstructor
	private static class EmbeddingUsage {
		@JsonProperty("prompt_tokens")
		private int promptTokens;
		@JsonProperty("total_tokens")
		private int totalTokens;
	}
}
