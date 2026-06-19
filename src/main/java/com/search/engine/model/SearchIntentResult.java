package com.search.engine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchIntentResult {
	private String canonicalQuery;
	private List<String> expandedTerms = new ArrayList<>();
	private List<String> suggestions = new ArrayList<>();

	public String expandedQuery() {
		List<String> terms = new ArrayList<>();
		if (canonicalQuery != null && !canonicalQuery.isBlank()) {
			terms.add(canonicalQuery);
		}
		terms.addAll(expandedTerms);
		return String.join(" ", terms.stream()
				.filter(term -> term != null && !term.isBlank())
				.distinct()
				.toList());
	}
}
