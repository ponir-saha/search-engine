package com.search.engine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemanticStatusResult {
	private boolean openAiConfigured;
	private int embeddingDimensions;
	private boolean openAiEmbeddingsWorking;
	private long weaviateProductObjects;
	private boolean semanticSearchReady;
}
