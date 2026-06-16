package com.search.engine.service;

import com.search.engine.model.ProductSuggestion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SearchIntentCatalog {

	private static final Map<String, List<String>> SEARCH_INTENTS = searchIntents();

	public String expandQuery(String query) {
		return String.join(" ", expandedTerms(query));
	}

	public List<ProductSuggestion> intentSuggestions(String query, int size) {
		String normalized = normalizeSearchText(query);
		if (normalized.length() < 2) {
			return List.of();
		}

		List<ProductSuggestion> suggestions = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : SEARCH_INTENTS.entrySet()) {
			String key = normalizeSearchText(entry.getKey());
			if (key.startsWith(normalized) || key.contains(normalized) || normalized.contains(key)) {
				String text = entry.getValue().isEmpty() ? entry.getKey() : entry.getValue().getFirst();
				suggestions.add(new ProductSuggestion("AI", null, text));
				for (String related : entry.getValue()) {
					suggestions.add(new ProductSuggestion("AI", null, related));
				}
			}
		}

		return suggestions.stream()
				.filter(suggestion -> suggestion.getText() != null && !suggestion.getText().isBlank())
				.filter(suggestion -> !normalizeSearchText(suggestion.getText()).equals(normalized))
				.collect(LinkedHashMap<String, ProductSuggestion>::new,
						(map, suggestion) -> map.putIfAbsent(normalizeSearchText(suggestion.getText()), suggestion),
						LinkedHashMap::putAll)
				.values()
				.stream()
				.limit(size)
				.toList();
	}

	public String productAliases(String text) {
		String normalized = normalizeSearchText(text);
		List<String> aliases = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : SEARCH_INTENTS.entrySet()) {
			String key = normalizeSearchText(entry.getKey());
			boolean productMatchesKey = normalized.contains(key);
			boolean productMatchesAlias = entry.getValue().stream()
					.map(this::normalizeSearchText)
					.anyMatch(normalized::contains);
			if (productMatchesKey || productMatchesAlias) {
				aliases.add(entry.getKey());
				aliases.addAll(entry.getValue());
			}
		}
		return String.join(" ", aliases.stream().distinct().toList());
	}

	private List<String> expandedTerms(String query) {
		String original = query == null ? "" : query.trim();
		String normalized = normalizeSearchText(original);
		List<String> terms = new ArrayList<>();
		if (!original.isBlank()) {
			terms.add(original);
		}
		if (!normalized.isBlank() && !normalized.equalsIgnoreCase(original)) {
			terms.add(normalized);
		}
		for (Map.Entry<String, List<String>> entry : SEARCH_INTENTS.entrySet()) {
			String key = normalizeSearchText(entry.getKey());
			if (normalized.equals(key) || normalized.contains(key) || key.startsWith(normalized)) {
				terms.addAll(entry.getValue());
			}
		}
		return terms.stream()
				.filter(term -> term != null && !term.isBlank())
				.distinct()
				.toList();
	}

	private String normalizeSearchText(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT)
				.replace('-', ' ')
				.replace('_', ' ')
				.replaceAll("\\s+", " ")
				.trim();
	}

	private static Map<String, List<String>> searchIntents() {
		Map<String, List<String>> intents = new LinkedHashMap<>();
		intents.put("lap", List.of("laptop", "notebook", "ultrabook", "macbook", "gaming laptop"));
		intents.put("laptop", List.of("notebook", "ultrabook", "macbook", "dell xps", "hp spectre", "thinkpad", "surface pro", "gaming laptop"));
		intents.put("notebook", List.of("laptop", "macbook", "dell xps", "hp spectre", "surface pro"));
		intents.put("computer", List.of("laptop", "macbook", "surface pro", "dell xps", "hp spectre"));
		intents.put("mobile charger", List.of("phone charger", "usb-c charger", "fast charger", "wireless charger", "power bank", "charging stand", "belkin boostcharge", "anker power bank"));
		intents.put("phone charger", List.of("mobile charger", "usb-c charger", "fast charger", "wireless charger", "power bank", "charging stand"));
		intents.put("charger", List.of("mobile charger", "phone charger", "usb-c charger", "fast charger", "wireless charger", "power bank", "charging stand", "adapter"));
		intents.put("type c", List.of("usb-c charger", "type-c charger", "phone charger", "fast charger", "charging cable", "power adapter", "power bank"));
		intents.put("type-c", List.of("usb-c charger", "type-c charger", "phone charger", "fast charger", "charging cable", "power adapter", "power bank"));
		intents.put("usb c", List.of("usb-c charger", "type-c charger", "phone charger", "fast charger", "charging cable", "power adapter", "power bank"));
		intents.put("usb-c", List.of("usb-c charger", "type-c charger", "phone charger", "fast charger", "charging cable", "power adapter", "power bank"));
		intents.put("mobile", List.of("phone", "smartphone", "iphone", "galaxy", "pixel", "oneplus", "motorola", "nokia", "xperia", "redmi"));
		intents.put("phone", List.of("mobile", "smartphone", "iphone", "galaxy", "pixel", "oneplus", "motorola", "nokia", "xperia", "redmi"));
		intents.put("smartphone", List.of("phone", "mobile", "iphone", "galaxy", "pixel", "oneplus"));
		intents.put("tablet", List.of("ipad", "tab", "fire hd", "surface pro"));
		intents.put("earphone", List.of("earbuds", "airpods", "galaxy buds", "headphones"));
		intents.put("headset", List.of("headphones", "earbuds", "airpods", "noise cancelling"));
		intents.put("shoe", List.of("shoes for men", "shoes for running", "running shoe", "sneaker", "trainer", "runner", "running", "footwear"));
		intents.put("shoes", List.of("shoes for men", "shoes for running", "running shoe", "sneaker", "trainer", "runner", "running", "footwear"));
		intents.put("running shoe", List.of("shoe", "shoes for running", "runner", "running", "sneaker", "trainer", "footwear"));
		intents.put("sneaker", List.of("shoe", "shoes", "running shoe", "trainer", "runner", "footwear"));
		intents.put("trainer", List.of("shoe", "shoes", "sneaker", "running shoe", "runner", "footwear"));
		intents.put("runner", List.of("shoe", "shoes", "running shoe", "shoes for running", "sneaker", "trainer", "footwear"));
		intents.put("running", List.of("shoe", "shoes", "running shoe", "shoes for running", "runner", "sneaker", "trainer", "footwear"));
		intents.put("footwear", List.of("shoe", "shoes", "sneaker", "trainer", "running shoe", "runner"));
		intents.put("wifi", List.of("router", "mesh router", "wi-fi 6", "networking"));
		intents.put("watch", List.of("smartwatch", "fitness tracker", "apple watch", "galaxy watch", "garmin", "fitbit"));
		return Collections.unmodifiableMap(intents);
	}
}
