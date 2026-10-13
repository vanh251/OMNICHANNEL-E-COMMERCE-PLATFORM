package com.example.productservice.event;

import com.example.productservice.config.CatalogRabbitConfig;
import com.example.productservice.entity.Product;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalog.inventory.sync.enabled", havingValue = "true", matchIfMissing = true)
public class InventorySyncAckConsumer {
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = CatalogRabbitConfig.ACK_QUEUE)
    public void receive(Message message) {
        try {
            JsonNode event = objectMapper.readTree(message.getBody());
            if (!"ProductInventorySynced".equals(event.path("eventType").asText())) {
                throw new IllegalArgumentException("Unexpected inventory acknowledgement");
            }
            String productId = event.path("productId").asText();
            String eventId = event.path("eventId").asText();
            if (productId.isBlank() || eventId.isBlank()) {
                throw new IllegalArgumentException("Missing inventory acknowledgement identifiers");
            }
            mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(productId)
                            .and("inventorySyncEventId").is(eventId)),
                    new Update().set("active", true).set("inventorySyncPublished", true), Product.class);
        } catch (IOException | IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid inventory acknowledgement", exception);
        }
    }
}
