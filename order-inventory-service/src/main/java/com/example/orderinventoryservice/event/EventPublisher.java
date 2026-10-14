package com.example.orderinventoryservice.event;

import com.example.orderinventoryservice.entity.OutboxEvent;
import com.example.orderinventoryservice.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void publishOrderCreatedEvent(OrderCreatedEvent event) {
        save("order.created", event);
    }

    public void publishOrderCancelledEvent(Object event) {
        save("order.cancelled", event);
    }

    public void publishOrderShippedEvent(Object event) {
        save("order.shipped", event);
    }

    public void publishOrderCompletedEvent(Object event) {
        save("order.completed", event);
    }

    public void publishInventorySyncedEvent(Object event) {
        save("catalog.inventory.synced", event);
    }

    private void save(String routingKey, Object event) {
        try {
            outboxEventRepository.save(new OutboxEvent(routingKey, objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize event " + routingKey, exception);
        }
    }
}
