package com.search.engine.consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.search.engine.model.ProductDto;
import com.search.engine.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaProductConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaProductConsumer.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final ProductService productService;

    public KafkaProductConsumer(ProductService productService) {
        this.productService = productService;
    }

    @KafkaListener(topics = "${app.kafka.topic:dbserver1.public.products}", groupId = "${app.kafka.group-id:search-engine-group}")
    public void handle(String message) {
        try {
            JsonNode root = mapper.readTree(message);

            JsonNode product = extractProductNode(root);
            if (product == null || product.isMissingNode() || product.isNull()) {
                log.debug("Ignoring CDC event without product row payload.");
                return;
            }

            Long id = product.path("id").isMissingNode() ? null : product.path("id").asLong();
            String name = product.path("name").asText(null);
            String description = product.path("description").asText(null);
            Double price = product.path("price").isMissingNode() ? null : product.path("price").asDouble();

            if (id == null) {
                log.warn("Product event without id: {}", message);
                return;
            }

            ProductDto p = new ProductDto(id, name, description, price);

            productService.indexProduct(p).block();

        } catch (Exception e) {
            log.error("Failed to process Kafka message", e);
            throw new IllegalStateException("Failed to process product CDC event", e);
        }
    }

    private JsonNode extractProductNode(JsonNode root) {
        JsonNode payloadAfter = root.path("payload").path("after");
        if (hasProductId(payloadAfter)) {
            return payloadAfter;
        }

        JsonNode payload = root.path("payload");
        if (hasProductId(payload)) {
            return payload;
        }

        JsonNode after = root.path("after");
        if (hasProductId(after)) {
            return after;
        }

        if (hasProductId(root)) {
            return root;
        }

        return null;
    }

    private boolean hasProductId(JsonNode node) {
        return !node.isMissingNode() && !node.isNull() && !node.path("id").isMissingNode();
    }
}
