package com.example.orderinventoryservice.event;

import com.example.orderinventoryservice.entity.OutboxEvent;
import com.example.orderinventoryservice.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EventPublisherTest {
    @Test
    void orderCreatedIsStoredWithRoutingKeyAndContract() throws Exception {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        EventPublisher publisher = new EventPublisher(repository, new ObjectMapper());
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId("event-1").eventType("OrderCreatedEvent").timestamp("2026-10-10T00:00:00Z")
                .data(OrderCreatedEvent.OrderCreatedEventData.builder()
                        .orderId("1").customerId("2").totalAmount(BigDecimal.TEN)
                        .currency("VND").items(List.of()).build()).build();

        publisher.publishOrderCreatedEvent(event);

        var captor = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRoutingKey()).isEqualTo("order.created");
        assertThat(new ObjectMapper().readTree(captor.getValue().getPayload())
                .path("data").path("orderId").asText()).isEqualTo("1");
        assertThat(captor.getValue().isPublished()).isFalse();
    }
}
