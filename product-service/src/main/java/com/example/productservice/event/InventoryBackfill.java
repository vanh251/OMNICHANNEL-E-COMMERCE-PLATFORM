package com.example.productservice.event;

import com.example.productservice.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalog.inventory.sync.backfill-enabled", havingValue = "true")
public class InventoryBackfill {
    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void queueExistingProducts() {
        Query missingEvent = Query.query(Criteria.where("inventorySyncEventId").exists(false));
        for (Product product : mongoTemplate.find(missingEvent, Product.class)) {
            if (product.getVariants() == null || product.getVariants().isEmpty()) continue;
            mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(product.getId())
                            .and("inventorySyncEventId").exists(false)),
                    new Update().set("inventorySyncEventId", UUID.randomUUID().toString())
                            .set("inventorySyncPublished", false).set("active", false), Product.class);
        }
    }
}
