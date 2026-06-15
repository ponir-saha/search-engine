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
public class SuggestionResponse {
    private String query;
    private List<@NonNull ProductSuggestion> suggestions = new ArrayList<>();
}
