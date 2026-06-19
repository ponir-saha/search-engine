package com.search.engine.client.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.search.engine.model.SearchIntentResult;
import com.search.engine.service.AiObservabilityService;
import tools.jackson.databind.ObjectMapper;
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
	private final String intentModel;
	private final AiObservabilityService observabilityService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public OpenAiClient(WebClient.Builder builder,
						@Value("${openai.api-key:}") String apiKey,
						@Value("${openai.intent-model:gpt-4.1-mini}") String intentModel,
						AiObservabilityService observabilityService) {
		this.webClient = builder.baseUrl("https://api.openai.com").build();
		this.apiKey = apiKey;
		this.intentModel = intentModel;
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

	public Mono<SearchIntentResult> generateSearchIntent(String query) {
		if (!isConfigured()) {
			return Mono.error(new IllegalStateException("OpenAI API key is not configured"));
		}

		Instant startedAt = Instant.now();
		Map<String, Object> schema = Map.of(
				"type", "object",
				"additionalProperties", false,
				"properties", Map.of(
						"canonicalQuery", Map.of("type", "string"),
						"expandedTerms", Map.of(
								"type", "array",
								"items", Map.of("type", "string"),
								"minItems", 3,
								"maxItems", 12
						),
						"suggestions", Map.of(
								"type", "array",
								"items", Map.of("type", "string"),
								"minItems", 4,
								"maxItems", 4
						)
				),
				"required", List.of("canonicalQuery", "expandedTerms", "suggestions")
		);

		String instructions = """
				You generate ecommerce product-search intent.
				Return a canonical query, useful semantic expansion terms, and exactly four short autocomplete suggestions.
				Suggestions must be search phrases, not claims that a specific product exists.
				Prefer common category names, synonyms, use cases, compatible terms, and product vocabulary.
				Do not add explanations.
				""";

		Map<String, Object> payload = Map.of(
				"model", intentModel,
				"instructions", instructions,
				"input", query,
				"text", Map.of(
						"format", Map.of(
								"type", "json_schema",
								"name", "search_intent",
								"strict", true,
								"schema", schema
						)
				)
		);

		return webClient.post()
				.uri("/v1/responses")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(payload)
				.retrieve()
				.onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
						.defaultIfEmpty("OpenAI intent request failed")
						.flatMap(body -> Mono.error(new IllegalStateException(body))))
				.bodyToMono(IntentResponse.class)
				.map(response -> {
					String outputText = response.outputText();
					if (outputText.isBlank()) {
						throw new IllegalStateException("OpenAI returned no structured search intent");
					}
					SearchIntentResult result = objectMapper.readValue(outputText, SearchIntentResult.class);
					observabilityService.recordOpenAiIntent(intentModel, query, Duration.between(startedAt, Instant.now()),
							response.getUsage().getInputTokens(), response.getUsage().getOutputTokens(),
							response.getUsage().getTotalTokens(), true, null);
					return result;
				})
				.doOnError(error -> observabilityService.recordOpenAiIntent(intentModel, query,
						Duration.between(startedAt, Instant.now()), 0, 0, 0, false, error));
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

	@Data
	@NoArgsConstructor
	private static class IntentResponse {
		private List<IntentOutput> output = new ArrayList<>();
		private IntentUsage usage = new IntentUsage();

		private String outputText() {
			return output.stream()
					.flatMap(item -> item.getContent().stream())
					.filter(content -> "output_text".equals(content.getType()))
					.map(IntentContent::getText)
					.filter(text -> text != null && !text.isBlank())
					.findFirst()
					.orElse("");
		}
	}

	@Data
	@NoArgsConstructor
	private static class IntentOutput {
		private List<IntentContent> content = new ArrayList<>();
	}

	@Data
	@NoArgsConstructor
	private static class IntentContent {
		private String type;
		private String text;
	}

	@Data
	@NoArgsConstructor
	private static class IntentUsage {
		@JsonProperty("input_tokens")
		private int inputTokens;
		@JsonProperty("output_tokens")
		private int outputTokens;
		@JsonProperty("total_tokens")
		private int totalTokens;
	}
}
