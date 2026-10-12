package com.example.paymentservice.event;

import com.example.paymentservice.entity.OutboxEvent;
import com.example.paymentservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final RabbitOperations operations = mock(RabbitOperations.class);
    private final OutboxPublisher publisher = new OutboxPublisher(repository, template, 100, 3000);

    private List<OutboxEvent> pendingEvents() {
        List<OutboxEvent> events = List.of(new OutboxEvent("order.created", "{\"eventId\":\"1\"}"),
                new OutboxEvent("payment.succeeded", "{\"eventId\":\"2\"}"));
        when(repository.findPendingBatch(100)).thenReturn(events);
        when(template.invoke(any())).thenAnswer(invocation -> {
            RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(operations);
        });
        return events;
    }

    @Test
    void emptyBatchDoesNotOpenRabbitChannel() {
        when(repository.findPendingBatch(100)).thenReturn(List.of());
        publisher.publishPending();
        verifyNoInteractions(template);
    }

    @Test
    void entireBatchIsConfirmedBeforeAnyEventIsMarkedPublished() {
        List<OutboxEvent> events = pendingEvents();
        doAnswer(invocation -> {
            assertThat(events).noneMatch(OutboxEvent::isPublished);
            return null;
        }).when(operations).waitForConfirmsOrDie(3000);

        publisher.publishPending();

        assertThat(events).allMatch(OutboxEvent::isPublished);
        var order = inOrder(operations);
        for (OutboxEvent event : events) {
            order.verify(operations).send(eq("ecommerce.exchange"), eq(event.getRoutingKey()),
                    argThat((Message message) -> message.getMessageProperties().getDeliveryMode()
                            == MessageDeliveryMode.PERSISTENT
                            && message.getMessageProperties().getContentType().equals("application/json")
                            && new String(message.getBody(), StandardCharsets.UTF_8).equals(event.getPayload())));
        }
        order.verify(operations).waitForConfirmsOrDie(3000);
        verify(template).invoke(any());
    }

    @Test
    void failedConfirmationKeepsEntireBatchPendingAndRetrySucceeds() {
        List<OutboxEvent> events = pendingEvents();
        doThrow(new IllegalStateException("Broker confirm timeout")).doNothing()
                .when(operations).waitForConfirmsOrDie(3000);

        publisher.publishPending();
        assertThat(events).noneMatch(OutboxEvent::isPublished);
        publisher.publishPending();
        assertThat(events).allMatch(OutboxEvent::isPublished);
        verify(operations, times(4)).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void partialSendFailureKeepsEvenPreviouslySentEventsPending() {
        List<OutboxEvent> events = pendingEvents();
        doNothing().doThrow(new IllegalStateException("Connection lost"))
                .when(operations).send(anyString(), anyString(), any(Message.class));
        publisher.publishPending();
        assertThat(events).noneMatch(OutboxEvent::isPublished);
        verify(operations, never()).waitForConfirmsOrDie(anyLong());
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertThatThrownBy(() -> new OutboxPublisher(repository, template, 0, 3000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxPublisher(repository, template, 1001, 3000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxPublisher(repository, template, 100, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
