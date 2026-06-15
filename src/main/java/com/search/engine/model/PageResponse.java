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
public class PageResponse<T extends @NonNull Object> {
    private List<@NonNull T> items = new ArrayList<>();
    private int page;
    private int size;
    private long totalElements;
}
