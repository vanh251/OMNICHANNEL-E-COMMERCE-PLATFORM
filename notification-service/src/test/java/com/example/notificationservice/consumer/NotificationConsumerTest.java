package com.example.notificationservice.consumer;

import com.example.notificationservice.model.EmailDelivery;
import com.example.notificationservice.repository.EmailDeliveryRepository;
import com.example.notificationservice.repository.OrderRecipientRepository;
import com.example.notificationservice.repository.VerifiedEmailRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {
    @Mock private OrderRecipientRepository recipients;
    @Mock private VerifiedEmailRepository verifiedEmails;
    @Mock private EmailDeliveryRepository deliveries;
    @Mock private RabbitTemplate rabbitTemplate;
    @InjectMocks private NotificationConsumer consumer;

    @Test
    void storesOrderNotificationBeforeRecipientEventAndIgnoresBrokerRedelivery() {
        Message event = message("""
                {"eventId":"event-42","eventType":"OrderCreatedEvent",
                 "data":{"orderId":"42","customerId":"7"}}
                """);
        consumer.consumeEvent(event);
        ArgumentCaptor<EmailDelivery> captured = ArgumentCaptor.forClass(EmailDelivery.class);
        verify(deliveries).save(captured.capture());
        assertThat(captured.getValue().getOrderId()).isEqualTo("42");
        assertThat(captured.getValue().getStatus()).isEqualTo("PENDING");
        verifyNoInteractions(recipients, verifiedEmails);

        doThrow(new DuplicateKeyException("eventId already exists")).when(deliveries).save(any());
        consumer.consumeEvent(event);
        verify(deliveries, times(2)).save(any());
        verifyNoInteractions(rabbitTemplate);
    }

    private Message message(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
