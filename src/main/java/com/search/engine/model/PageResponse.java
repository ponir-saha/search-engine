package com.search.engine.model;

import java.util.List;

public class PageResponse<T> {
    private List<T> items;
    private int page;
    private int size;
    private long totalElements;

    public PageResponse() {}

    public PageResponse(List<T> items, int page, int size, long totalElements) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}

