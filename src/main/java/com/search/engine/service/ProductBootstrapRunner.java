package com.search.engine.service;

import com.search.engine.model.ProductDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
public class ProductBootstrapRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ProductBootstrapRunner.class);

	private final boolean enabled;
	private final boolean reset;
	private final boolean waitForIndexes;
	private final Duration waitTimeout;
	private final Resource dataset;
	private final ProductDatasetLoader datasetLoader;
	private final ProductService productService;

	public ProductBootstrapRunner(@Value("${app.bootstrap.enabled:true}") boolean enabled,
								  @Value("${app.bootstrap.reset:true}") boolean reset,
								  @Value("${app.bootstrap.wait-for-indexes:true}") boolean waitForIndexes,
								  @Value("${app.bootstrap.wait-timeout:PT15M}") Duration waitTimeout,
								  @Value("${app.bootstrap.dataset:classpath:data/products.tsv}") Resource dataset,
								  ProductDatasetLoader datasetLoader,
								  ProductService productService) {
		this.enabled = enabled;
		this.reset = reset;
		this.waitForIndexes = waitForIndexes;
		this.waitTimeout = waitTimeout;
		this.dataset = dataset;
		this.datasetLoader = datasetLoader;
		this.productService = productService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!enabled) {
			log.info("Product bootstrap is disabled.");
			return;
		}

		bootstrap().block();
	}

	private Mono<Void> bootstrap() {
		return datasetLoader.load(dataset)
				.flatMap(products -> {
					log.info("Starting product bootstrap with {} products from {}", products.size(), dataset.getDescription());
					Mono<Void> resetStep = reset ? productService.resetProductStores() : Mono.empty();
					return resetStep
							.then(insertProducts(products))
							.then(waitForIndexes(products.size()))
							.doOnSuccess(ignored -> log.info("Product bootstrap completed for {} products.", products.size()));
				});
	}

	private Mono<Void> insertProducts(List<ProductDto> products) {
		return productService.createProductTable()
				.thenMany(Flux.fromIterable(products)
						.concatMap(productService::saveProductOnly))
				.then();
	}

	private Mono<Void> waitForIndexes(int expectedProducts) {
		if (!waitForIndexes) {
			return Mono.empty();
		}
		return productService.waitForSearchStoreCounts(expectedProducts, waitTimeout);
	}
}
