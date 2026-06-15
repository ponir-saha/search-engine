package com.search.engine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSyncResult {
	private long databaseProducts;
	private long weaviateProductsBefore;
	private long weaviateProductsAfter;
	private int syncedProducts;
	private int failedProducts;
	private boolean openAiConfigured;
	private int embeddingDimensions;
	private boolean openAiEmbeddingsWorking;
	private List<@NonNull String> errors = new ArrayList<>();
}
