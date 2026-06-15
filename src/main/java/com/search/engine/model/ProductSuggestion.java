package com.search.engine.model;

public class ProductSuggestion {
    private String type;
    private Long productId;
    private String text;

    public ProductSuggestion() {}

    public ProductSuggestion(String type, Long productId, String text) {
        this.type = type;
        this.productId = productId;
        this.text = text;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
