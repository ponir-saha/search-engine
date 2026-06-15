package com.search.engine.model;

import java.util.List;

public class ProductSyncResult {
	private long databaseProducts;
	private long weaviateProductsBefore;
	private long weaviateProductsAfter;
	private int syncedProducts;
	private int failedProducts;
	private boolean openAiConfigured;
	private int embeddingDimensions;
	private boolean openAiEmbeddingsWorking;
	private List<String> errors;

	public ProductSyncResult() {
	}

	public ProductSyncResult(long databaseProducts,
							 long weaviateProductsBefore,
							 long weaviateProductsAfter,
							 int syncedProducts,
							 int failedProducts,
							 boolean openAiConfigured,
							 int embeddingDimensions,
							 boolean openAiEmbeddingsWorking,
							 List<String> errors) {
		this.databaseProducts = databaseProducts;
		this.weaviateProductsBefore = weaviateProductsBefore;
		this.weaviateProductsAfter = weaviateProductsAfter;
		this.syncedProducts = syncedProducts;
		this.failedProducts = failedProducts;
		this.openAiConfigured = openAiConfigured;
		this.embeddingDimensions = embeddingDimensions;
		this.openAiEmbeddingsWorking = openAiEmbeddingsWorking;
		this.errors = errors;
	}

	public long getDatabaseProducts() {
		return databaseProducts;
	}

	public void setDatabaseProducts(long databaseProducts) {
		this.databaseProducts = databaseProducts;
	}

	public long getWeaviateProductsBefore() {
		return weaviateProductsBefore;
	}

	public void setWeaviateProductsBefore(long weaviateProductsBefore) {
		this.weaviateProductsBefore = weaviateProductsBefore;
	}

	public long getWeaviateProductsAfter() {
		return weaviateProductsAfter;
	}

	public void setWeaviateProductsAfter(long weaviateProductsAfter) {
		this.weaviateProductsAfter = weaviateProductsAfter;
	}

	public int getSyncedProducts() {
		return syncedProducts;
	}

	public void setSyncedProducts(int syncedProducts) {
		this.syncedProducts = syncedProducts;
	}

	public int getFailedProducts() {
		return failedProducts;
	}

	public void setFailedProducts(int failedProducts) {
		this.failedProducts = failedProducts;
	}

	public boolean isOpenAiConfigured() {
		return openAiConfigured;
	}

	public void setOpenAiConfigured(boolean openAiConfigured) {
		this.openAiConfigured = openAiConfigured;
	}

	public int getEmbeddingDimensions() {
		return embeddingDimensions;
	}

	public void setEmbeddingDimensions(int embeddingDimensions) {
		this.embeddingDimensions = embeddingDimensions;
	}

	public boolean isOpenAiEmbeddingsWorking() {
		return openAiEmbeddingsWorking;
	}

	public void setOpenAiEmbeddingsWorking(boolean openAiEmbeddingsWorking) {
		this.openAiEmbeddingsWorking = openAiEmbeddingsWorking;
	}

	public List<String> getErrors() {
		return errors;
	}

	public void setErrors(List<String> errors) {
		this.errors = errors;
	}
}
