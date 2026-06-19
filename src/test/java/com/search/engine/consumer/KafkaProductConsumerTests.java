package com.search.engine.consumer;

import com.search.engine.model.ProductDto;
import com.search.engine.service.ProductService;
import com.search.engine.service.SearchIntentCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProductConsumerTests {

	private final FakeProductService productService = new FakeProductService();
	private final KafkaProductConsumer consumer = new KafkaProductConsumer(productService);

	@Test
	void indexesUnwrappedCreateOrUpdateEvents() {
		consumer.handle("""
				{"id":9001,"name":"CDC Test Phone","description":"Phone from CDC","price":199.99}
				""");

		assertThat(productService.indexedProducts)
				.extracting(ProductDto::getId)
				.containsExactly(9001L);
		assertThat(productService.deletedProductIds).isEmpty();
	}

	@Test
	void deletesUnwrappedRewriteDeleteEvents() {
		consumer.handle("""
				{"id":9001,"name":"CDC Test Phone","description":"Phone from CDC","price":199.99,"__deleted":"true"}
				""");

		assertThat(productService.deletedProductIds).containsExactly(9001L);
		assertThat(productService.indexedProducts).isEmpty();
	}

	@Test
	void deletesEnvelopeDeleteEvents() {
		consumer.handle("""
				{"payload":{"before":{"id":9002,"name":"Old product","description":"Deleted","price":1.0},"after":null,"op":"d"}}
				""");

		assertThat(productService.deletedProductIds).containsExactly(9002L);
		assertThat(productService.indexedProducts).isEmpty();
	}

	private static class FakeProductService extends ProductService {
		private final List<ProductDto> indexedProducts = new ArrayList<>();
		private final List<Long> deletedProductIds = new ArrayList<>();

		FakeProductService() {
			super(null, WebClient.builder(), "http://127.0.0.1:9200", "products", null, null,
					new SearchIntentCatalog(), null, null);
		}

		@Override
		public Mono<Void> indexProductFromCdc(ProductDto product) {
			indexedProducts.add(product);
			return Mono.empty();
		}

		@Override
		public Mono<Void> deleteProductFromSearchStoresStrict(Long id) {
			deletedProductIds.add(id);
			return Mono.empty();
		}
	}
}
