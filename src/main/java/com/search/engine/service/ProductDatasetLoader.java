package com.search.engine.service;

import com.search.engine.model.ProductDto;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class ProductDatasetLoader {

	public Mono<List<ProductDto>> load(Resource resource) {
		return Mono.fromCallable(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
				return reader.lines()
						.skip(1)
						.filter(line -> !line.isBlank())
						.map(this::parseProduct)
						.toList();
			}
		}).subscribeOn(Schedulers.boundedElastic());
	}

	private ProductDto parseProduct(String line) {
		String[] columns = line.split("\\t", -1);
		if (columns.length != 4) {
			throw new IllegalArgumentException("Invalid product row: " + line);
		}
		return new ProductDto(
				Long.valueOf(columns[0]),
				columns[1],
				columns[2],
				Double.valueOf(columns[3])
		);
	}
}
