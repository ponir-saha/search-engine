package com.search.engine.model;

import java.util.List;

public class SuggestionResponse {
    private String query;
    private List<ProductSuggestion> suggestions;

    public SuggestionResponse() {}

    public SuggestionResponse(String query, List<ProductSuggestion> suggestions) {
        this.query = query;
        this.suggestions = suggestions;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<ProductSuggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<ProductSuggestion> suggestions) {
        this.suggestions = suggestions;
    }
}
