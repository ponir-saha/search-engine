package com.search.engine.service;

import com.search.engine.model.ProductSuggestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchIntentCatalogTests {

	private final SearchIntentCatalog catalog = new SearchIntentCatalog();

	@Test
	void expandsShortLaptopIntent() {
		assertThat(catalog.expandQuery("lap"))
				.contains("laptop")
				.contains("macbook")
				.contains("gaming laptop");
	}

	@Test
	void expandsTypeCToChargingIntent() {
		assertThat(catalog.expandQuery("type-c"))
				.contains("usb-c charger")
				.contains("phone charger")
				.contains("power bank");
	}

	@Test
	void suggestsAiIntentBeforeProductLookup() {
		List<ProductSuggestion> suggestions = catalog.intentSuggestions("lap", 2);

		assertThat(suggestions)
				.extracting(ProductSuggestion::getText)
				.containsExactly("laptop", "notebook");
		assertThat(suggestions)
				.extracting(ProductSuggestion::getType)
				.containsOnly("AI");
	}

	@Test
	void enrichesProductTextWithRelatedSearchTerms() {
		assertThat(catalog.productAliases("Belkin BoostCharge Pro fast wireless charging stand"))
				.contains("mobile charger")
				.contains("usb-c charger")
				.contains("power bank");
	}
}
