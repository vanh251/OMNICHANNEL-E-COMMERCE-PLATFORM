package com.example.productservice.event;

import com.example.productservice.config.CatalogRabbitConfig;
import com.example.productservice.entity.Product;
import com.example.productservice.entity.ProductVariant;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalog.inventory.sync.enabled", havingValue = "true", matchIfMissing = true)
public class InventorySyncPublisher {
    private final MongoTemplate mongoTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${catalog.inventory.sync.poll-ms:1000}")
    public void publishPending() {
        Query query = Query.query(Criteria.where("inventorySyncEventId").exists(true)
                .and("inventorySyncPublished").is(false)).limit(100);
        for (Product product : mongoTemplate.find(query, Product.class)) {
            try {
                InventorySyncEvent event = new InventorySyncEvent(
                        product.getInventorySyncEventId(), "ProductInventorySyncRequested", product.getId(),
                        product.getVariants().stream()
                                .map(variant -> new InventorySyncEvent.Variant(variant.getSku(), variant.getStock()))
                                .toList());
                MessageProperties properties = new MessageProperties();
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                Message message = new Message(objectMapper.writeValueAsBytes(event), properties);
                rabbitTemplate.invoke(operations -> {
                    operations.send(CatalogRabbitConfig.EXCHANGE, CatalogRabbitConfig.SYNC_KEY, message);
                    operations.waitForConfirmsOrDie(5000);
                    return null;
                });
                // The event ID guard leaves a newer update pending if it raced this publish.
                mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(product.getId())
                                .and("inventorySyncEventId").is(product.getInventorySyncEventId())),
                        new Update().set("inventorySyncPublished", true), Product.class);
            } catch (Exception exception) {
                log.warn("Inventory sync publish failed for product {}. Will retry.", product.getId(), exception);
            }
        }
    }
}
