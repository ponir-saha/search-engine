package com.search.engine.service;

import com.search.engine.model.AiFeedbackRequest;
import com.search.engine.model.ProductDto;
import com.search.engine.model.ProductSuggestion;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AiObservabilityService {

	private static final Logger log = LoggerFactory.getLogger(AiObservabilityService.class);

	private final MeterRegistry registry;
	private final SearchIntentCatalog searchIntentCatalog;

	public AiObservabilityService(MeterRegistry registry, SearchIntentCatalog searchIntentCatalog) {
		this.registry = registry;
		this.searchIntentCatalog = searchIntentCatalog;
	}

	public void recordOpenAiEmbedding(String model,
									  String input,
									  Duration latency,
									  int promptTokens,
									  int totalTokens,
									  boolean success,
									  Throwable error) {
		String status = success ? "success" : "error";
		Counter.builder("ai_openai_requests_total")
				.description("Total OpenAI API requests by operation, model and status.")
				.tag("operation", "embedding")
				.tag("model", model)
				.tag("status", status)
				.register(registry)
				.increment();

		Timer.builder("ai_openai_latency")
				.description("OpenAI API latency by operation and model.")
				.tag("operation", "embedding")
				.tag("model", model)
				.tag("status", status)
				.publishPercentileHistogram()
				.register(registry)
				.record(latency.toNanos(), TimeUnit.NANOSECONDS);

		recordTokenCounter(model, "prompt", promptTokens);
		recordTokenCounter(model, "total", totalTokens);

		Span span = Span.current();
		span.setAttribute("ai.operation", "embedding");
		span.setAttribute("ai.model", model);
		span.setAttribute("ai.input.length", safeText(input).length());
		span.setAttribute("ai.openai.prompt_tokens", promptTokens);
		span.setAttribute("ai.openai.total_tokens", totalTokens);
		span.setAttribute("ai.openai.latency_ms", latency.toMillis());
		span.setAttribute("ai.openai.status", status);
		if (error != null) {
			span.recordException(error);
			span.setAttribute("ai.openai.error_type", error.getClass().getName());
		}
	}

	public void recordSearch(String mode,
							 String query,
							 String expandedQuery,
							 List<ProductDto> products,
							 Duration latency,
							 Throwable error) {
		String status = error == null ? "success" : "error";
		List<ProductDto> safeProducts = products == null ? List.of() : products;
		QualitySignals quality = scoreSearch(query, expandedQuery, safeProducts);

		Counter.builder("ai_search_requests_total")
				.description("Total AI-backed search requests by mode and status.")
				.tag("mode", safeMode(mode))
				.tag("status", status)
				.register(registry)
				.increment();

		Timer.builder("ai_search_latency")
				.description("AI-backed search latency by mode and status.")
				.tag("mode", safeMode(mode))
				.tag("status", status)
				.publishPercentileHistogram()
				.register(registry)
				.record(latency.toNanos(), TimeUnit.NANOSECONDS);

		summary("ai_search_results", "Number of products returned by AI-backed search.", safeProducts.size());
		summary("ai_retrieval_groundedness_score", "Proxy groundedness score for returned product results.", quality.groundedness());
		summary("ai_retrieval_query_coverage_score", "Query-term coverage score for returned product results.", quality.queryCoverage());
		summary("ai_hallucination_rate", "Proxy hallucination rate for ungrounded or weakly matched AI search results.", quality.hallucinationRate());
		summary("ai_answer_quality_score", "Composite AI search quality score from groundedness, coverage and result presence.", quality.answerQuality());

		Span span = Span.current();
		span.setAttribute("ai.search.mode", safeMode(mode));
		span.setAttribute("ai.search.query_length", safeText(query).length());
		span.setAttribute("ai.search.expanded_query_length", safeText(expandedQuery).length());
		span.setAttribute("ai.search.result_count", safeProducts.size());
		span.setAttribute("ai.search.groundedness_score", quality.groundedness());
		span.setAttribute("ai.search.query_coverage_score", quality.queryCoverage());
		span.setAttribute("ai.search.hallucination_rate", quality.hallucinationRate());
		span.setAttribute("ai.search.answer_quality_score", quality.answerQuality());
		if (error != null) {
			span.recordException(error);
			span.setAttribute("ai.search.error_type", error.getClass().getName());
		}
	}

	public void recordSuggestions(String query,
								  List<ProductSuggestion> suggestions,
								  Duration latency,
								  Throwable error) {
		String status = error == null ? "success" : "error";
		List<ProductSuggestion> safeSuggestions = suggestions == null ? List.of() : suggestions;

		Counter.builder("ai_suggestions_requests_total")
				.description("Total suggestion requests using AI intent and semantic retrieval.")
				.tag("status", status)
				.register(registry)
				.increment();

		Timer.builder("ai_suggestions_latency")
				.description("Suggestion request latency.")
				.tag("status", status)
				.publishPercentileHistogram()
				.register(registry)
				.record(latency.toNanos(), TimeUnit.NANOSECONDS);

		summary("ai_suggestions_results", "Number of suggestions returned.", safeSuggestions.size());
		long aiSuggestions = safeSuggestions.stream()
				.filter(suggestion -> "AI".equalsIgnoreCase(suggestion.getType()) || "SEMANTIC".equalsIgnoreCase(suggestion.getType()))
				.count();
		summary("ai_suggestions_ai_ratio", "Ratio of AI/semantic suggestions in the suggestion response.",
				safeSuggestions.isEmpty() ? 0.0 : (double) aiSuggestions / safeSuggestions.size());

		Span span = Span.current();
		span.setAttribute("ai.suggestions.query_length", safeText(query).length());
		span.setAttribute("ai.suggestions.result_count", safeSuggestions.size());
		span.setAttribute("ai.suggestions.ai_result_count", aiSuggestions);
		if (error != null) {
			span.recordException(error);
			span.setAttribute("ai.suggestions.error_type", error.getClass().getName());
		}
	}

	public void recordFeedback(AiFeedbackRequest feedback) {
		String mode = safeMode(feedback.getMode());
		String relevant = String.valueOf(Boolean.TRUE.equals(feedback.getRelevant()));
		String grounded = String.valueOf(Boolean.TRUE.equals(feedback.getGrounded()));
		String rating = feedback.getRating() == null ? "unknown" : String.valueOf(feedback.getRating());

		Counter.builder("ai_feedback_total")
				.description("User feedback events for AI search quality.")
				.tag("mode", mode)
				.tag("rating", rating)
				.tag("relevant", relevant)
				.tag("grounded", grounded)
				.register(registry)
				.increment();

		if (feedback.getRating() != null) {
			summary("ai_feedback_rating", "User-provided AI answer quality rating from 1 to 5.", feedback.getRating());
		}
		summary("ai_feedback_relevance_score", "Binary user feedback relevance score.", Boolean.TRUE.equals(feedback.getRelevant()) ? 1.0 : 0.0);
		summary("ai_feedback_groundedness_score", "Binary user feedback groundedness score.", Boolean.TRUE.equals(feedback.getGrounded()) ? 1.0 : 0.0);

		Span span = Span.current();
		span.setAttribute("ai.feedback.mode", mode);
		span.setAttribute("ai.feedback.query_length", safeText(feedback.getQuery()).length());
		span.setAttribute("ai.feedback.product_id", feedback.getProductId() == null ? 0L : feedback.getProductId());
		span.setAttribute("ai.feedback.relevant", Boolean.TRUE.equals(feedback.getRelevant()));
		span.setAttribute("ai.feedback.grounded", Boolean.TRUE.equals(feedback.getGrounded()));
		span.setAttribute("ai.feedback.rating", feedback.getRating() == null ? 0L : feedback.getRating().longValue());

		log.info("AI feedback received. mode={}, rating={}, relevant={}, grounded={}, productId={}",
				mode, rating, relevant, grounded, feedback.getProductId());
	}

	private void recordTokenCounter(String model, String type, int tokens) {
		if (tokens <= 0) {
			return;
		}
		Counter.builder("ai_openai_tokens_total")
				.description("OpenAI token consumption by operation, model and token type.")
				.tag("operation", "embedding")
				.tag("model", model)
				.tag("type", type)
				.register(registry)
				.increment(tokens);
	}

	private void summary(String name, String description, double value) {
		DistributionSummary.builder(name)
				.description(description)
				.register(registry)
				.record(value);
	}

	private QualitySignals scoreSearch(String query, String expandedQuery, List<ProductDto> products) {
		if (products.isEmpty()) {
			return new QualitySignals(1.0, 0.0, 0.0, 0.5);
		}

		Set<String> queryTerms = tokenize(safeText(query) + " " + safeText(expandedQuery));
		double groundedProducts = 0.0;
		Set<String> matchedTerms = new HashSet<>();

		for (ProductDto product : products) {
			String productText = safeText(product.getName()) + " " + safeText(product.getDescription());
			String productTextWithAliases = productText + " " + searchIntentCatalog.productAliases(productText);
			boolean hasProductIdentity = product.getId() != null
					&& !safeText(product.getName()).isBlank()
					&& !safeText(product.getDescription()).isBlank()
					&& searchIntentCatalog.productMatchesQueryIntent(query, productText);
			if (hasProductIdentity) {
				groundedProducts++;
			}
			Set<String> productTerms = tokenize(productTextWithAliases);
			for (String term : queryTerms) {
				if (productTerms.contains(term)) {
					matchedTerms.add(term);
				}
			}
		}

		double groundedness = groundedProducts / products.size();
		double queryCoverage = queryTerms.isEmpty() ? 0.0 : (double) matchedTerms.size() / queryTerms.size();
		double hallucinationRate = 1.0 - (groundedness * Math.max(queryCoverage, 0.8));
		double answerQuality = (groundedness * 0.5) + (queryCoverage * 0.3) + 0.2;

		return new QualitySignals(clamp(groundedness), clamp(queryCoverage), clamp(hallucinationRate), clamp(answerQuality));
	}

	private Set<String> tokenize(String text) {
		return java.util.Arrays.stream(safeText(text).toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
				.filter(token -> token.length() >= 3)
				.collect(Collectors.toCollection(HashSet::new));
	}

	private double clamp(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}

	private String safeMode(String mode) {
		String normalized = safeText(mode).trim().toLowerCase(Locale.ROOT);
		return normalized.isBlank() ? "unknown" : normalized;
	}

	private String safeText(String value) {
		return value == null ? "" : value;
	}

	private record QualitySignals(double groundedness, double queryCoverage, double hallucinationRate, double answerQuality) {
	}
}
