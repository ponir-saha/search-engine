package com.search.engine.service;

import com.search.engine.model.SearchIntentResult;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SearchIntentCatalog {

	public SearchIntentResult fallback(String query) {
		String normalized = normalizeSearchText(query);
		return new SearchIntentResult(normalized, List.of(), List.of());
	}

	public SearchIntentResult sanitize(String query, SearchIntentResult intent) {
		if (intent == null) {
			return fallback(query);
		}

		String canonical = normalizeSearchText(intent.getCanonicalQuery());
		if (canonical.isBlank()) {
			canonical = normalizeSearchText(query);
		}

		List<String> expandedTerms = normalizeTerms(intent.getExpandedTerms(), 12);
		List<String> suggestions = normalizeTerms(intent.getSuggestions(), 4);
		return new SearchIntentResult(canonical, expandedTerms, suggestions);
	}

	public boolean productMatchesIntent(SearchIntentResult intent, String productText) {
		if (intent == null) {
			return false;
		}
		String normalizedProduct = normalizeSearchText(productText);
		if (normalizedProduct.isBlank()) {
			return false;
		}

		Set<String> phrases = new LinkedHashSet<>();
		phrases.add(normalizeSearchText(intent.getCanonicalQuery()));
		for (String term : intent.getExpandedTerms()) {
			phrases.add(normalizeSearchText(term));
		}
		return phrases.stream()
				.filter(phrase -> !phrase.isBlank())
				.anyMatch(phrase -> phrasePresent(normalizedProduct, phrase));
	}

	public boolean productMatchesExpandedQuery(String expandedQuery, String productText) {
		SearchIntentResult intent = new SearchIntentResult(
				expandedQuery,
				Arrays.stream(normalizeSearchText(expandedQuery).split(" "))
						.filter(term -> !term.isBlank())
						.toList(),
				List.of()
		);
		return productMatchesIntent(intent, productText);
	}

	public String normalizeSearchText(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT)
				.replace('-', ' ')
				.replace('_', ' ')
				.replaceAll("[^a-z0-9 ]", " ")
				.replaceAll("\\s+", " ")
				.trim();
	}

	private List<String> normalizeTerms(List<String> terms, int limit) {
		if (terms == null) {
			return List.of();
		}
		return terms.stream()
				.map(this::normalizeSearchText)
				.filter(term -> !term.isBlank())
				.distinct()
				.limit(limit)
				.collect(Collectors.toList());
	}

	private boolean phrasePresent(String text, String phrase) {
		List<String> textTokens = List.of(text.split(" "));
		List<String> phraseTokens = List.of(phrase.split(" "));
		if (phraseTokens.size() > textTokens.size()) {
			return false;
		}
		for (int i = 0; i <= textTokens.size() - phraseTokens.size(); i++) {
			if (textTokens.subList(i, i + phraseTokens.size()).equals(phraseTokens)) {
				return true;
			}
		}
		return false;
	}
}
