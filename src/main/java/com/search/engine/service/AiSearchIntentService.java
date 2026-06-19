package com.search.engine.service;

import com.search.engine.client.openai.OpenAiClient;
import com.search.engine.model.SearchIntentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiSearchIntentService {

	private static final Logger log = LoggerFactory.getLogger(AiSearchIntentService.class);

	private final OpenAiClient openAiClient;
	private final SearchIntentCatalog fallback;
	private final Duration cacheTtl;
	private final Map<String, CachedIntent> cache = new ConcurrentHashMap<>();

	public AiSearchIntentService(OpenAiClient openAiClient,
								 SearchIntentCatalog fallback,
								 @Value("${openai.intent-cache-ttl:PT15M}") Duration cacheTtl) {
		this.openAiClient = openAiClient;
		this.fallback = fallback;
		this.cacheTtl = cacheTtl;
	}

	public Mono<SearchIntentResult> resolve(String query) {
		String cacheKey = fallback.normalizeSearchText(query);
		if (cacheKey.isBlank()) {
			return Mono.just(fallback.fallback(query));
		}

		CachedIntent cached = cache.get(cacheKey);
		if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
			return Mono.just(cached.intent());
		}

		return openAiClient.generateSearchIntent(query)
				.map(intent -> fallback.sanitize(query, intent))
				.doOnNext(intent -> cache.put(cacheKey, new CachedIntent(intent, Instant.now().plus(cacheTtl))))
				.onErrorResume(error -> {
					log.warn("OpenAI search-intent generation failed; using the original query without AI suggestions. Cause: {}",
							safeErrorMessage(error));
					return Mono.just(fallback.fallback(query));
				});
	}

	private String safeErrorMessage(Throwable error) {
		String message = error.getMessage();
		if (message == null || message.isBlank()) {
			return error.getClass().getSimpleName();
		}
		return message.replaceAll("sk-[A-Za-z0-9_-]+", "[REDACTED]");
	}

	private record CachedIntent(SearchIntentResult intent, Instant expiresAt) {
	}
}
