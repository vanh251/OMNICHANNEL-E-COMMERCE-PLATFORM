package com.example.productservice.event;

import com.example.productservice.entity.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InventorySyncAckConsumerTest {
    @Test
    void acknowledgementOnlyActivatesMatchingProductEvent() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        InventorySyncAckConsumer consumer = new InventorySyncAckConsumer(mongoTemplate, new ObjectMapper());
        consumer.receive(new Message("""
                {"eventId":"event-2","eventType":"ProductInventorySynced","productId":"product-1"}
                """.getBytes()));

        org.mockito.ArgumentCaptor<Query> query = org.mockito.ArgumentCaptor.forClass(Query.class);
        org.mockito.ArgumentCaptor<Update> update = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(Product.class));
        assertThat(query.getValue().getQueryObject().getString("id")).isEqualTo("product-1");
        assertThat(query.getValue().getQueryObject().getString("inventorySyncEventId")).isEqualTo("event-2");
        assertThat(update.getValue().getUpdateObject().get("$set", org.bson.Document.class).getBoolean("active"))
                .isTrue();
    }
}
