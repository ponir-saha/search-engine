package com.search.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(scanBasePackages = {
		"com.search.engine.api",
		"com.search.engine.service",
		"com.search.engine.repository",
		"com.search.engine.model",
		"com.search.engine.client",
		"com.search.engine.consumer",
		"com.search.engine.config"
})
@EnableKafka
public class SearchEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(SearchEngineApplication.class, args);
	}

}
