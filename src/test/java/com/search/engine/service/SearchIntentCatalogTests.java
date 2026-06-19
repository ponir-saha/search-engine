package com.search.engine.service;

import com.search.engine.model.SearchIntentResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchIntentCatalogTests {

	private final SearchIntentCatalog catalog = new SearchIntentCatalog();

	@Test
	void fallsBackToNormalizedOriginalQuery() {
		SearchIntentResult result = catalog.fallback("  Type-C_Charger! ");

		assertThat(result.getCanonicalQuery()).isEqualTo("type c charger");
		assertThat(result.getExpandedTerms()).isEmpty();
		assertThat(result.getSuggestions()).isEmpty();
	}

	@Test
	void sanitizesModelGeneratedIntent() {
		SearchIntentResult result = catalog.sanitize("shoe", new SearchIntentResult(
				" Running Shoes ",
				List.of("Sneaker", "runner", "SNEAKER", "footwear"),
				List.of("Shoes for Men", "Running Shoes", "Sneakers", "Trail Shoes", "ignored")
		));

		assertThat(result.getCanonicalQuery()).isEqualTo("running shoes");
		assertThat(result.getExpandedTerms()).containsExactly("sneaker", "runner", "footwear");
		assertThat(result.getSuggestions())
				.containsExactly("shoes for men", "running shoes", "sneakers", "trail shoes");
	}

	@Test
	void matchesProductsUsingAiGeneratedIntentTerms() {
		SearchIntentResult intent = new SearchIntentResult(
				"shoe",
				List.of("running shoe", "sneaker", "runner", "footwear"),
				List.of()
		);

		assertThat(catalog.productMatchesIntent(
				intent,
				"Nike Runner is designed for daily comfort and road training"
		)).isTrue();
		assertThat(catalog.productMatchesIntent(
				intent,
				"Garmin Forerunner smartwatch with training metrics"
		)).isFalse();
	}
}
