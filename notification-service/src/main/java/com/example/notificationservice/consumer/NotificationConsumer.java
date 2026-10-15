package com.example.notificationservice.consumer;

import com.example.notificationservice.config.RabbitMQConfig;
import com.example.notificationservice.model.EmailDelivery;
import com.example.notificationservice.model.OrderRecipient;
import com.example.notificationservice.model.VerifiedEmail;
import com.example.notificationservice.repository.EmailDeliveryRepository;
import com.example.notificationservice.repository.OrderRecipientRepository;
import com.example.notificationservice.repository.VerifiedEmailRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {
    private static final Set<String> ORDER_EMAIL_TYPES = Set.of(
            "OrderCreatedEvent", "PaymentSucceededEvent", "PaymentFailedEvent",
            "PaymentRefundedEvent", "OrderCancelledEvent", "OrderShippedEvent", "OrderCompletedEvent");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrderRecipientRepository recipients;
    private final VerifiedEmailRepository verifiedEmails;
    private final EmailDeliveryRepository deliveries;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void consumeEvent(Message message) {
        try {
            JsonNode event = objectMapper.readTree(message.getBody());
            if (event == null || !event.isObject()) {
                throw new IllegalArgumentException("Expected a notification event object");
            }
            String eventId = required(event, "eventId");
            String type = required(event, "eventType");
            JsonNode data = event.path("data");
            if (!data.isObject()) throw new IllegalArgumentException("Missing event data");
            if ("OrderEmailRecipientRegisteredEvent".equals(type)) {
                String orderId = required(data, "orderId");
                String customerId = required(data, "customerId");
                String email = email(required(data, "email"));
                OrderRecipient recipient = recipients.findById(orderId).orElseGet(OrderRecipient::new);
                if (recipient.getOrderId() != null && (!customerId.equals(recipient.getCustomerId())
                        || !email.equals(recipient.getEmail()))) {
                    throw new IllegalArgumentException("Conflicting order recipient");
                }
                recipient.setOrderId(orderId);
                recipient.setCustomerId(customerId);
                recipient.setEmail(email);
                recipients.save(recipient);
                return;
            }
            if ("CustomerEmailVerifiedEvent".equals(type)) {
                String customerId = required(data, "customerId");
                String email = email(required(data, "email"));
                VerifiedEmail verified = new VerifiedEmail();
                verified.setId(customerId + ":" + email);
                verified.setCustomerId(customerId);
                verified.setEmail(email);
                verifiedEmails.save(verified);
                return;
            }
            EmailDelivery delivery = new EmailDelivery();
            delivery.setEventId(eventId);
            delivery.setEventType(type);
            delivery.setCreatedAt(LocalDateTime.now());
            delivery.setNextAttemptAt(LocalDateTime.now());
            delivery.setStatus("PENDING");
            if ("EmailVerificationRequestedEvent".equals(type)) {
                delivery.setCustomerId(required(data, "customerId"));
                delivery.setEmail(email(required(data, "email")));
                delivery.setVerificationUrl(required(data, "verificationUrl"));
                delivery.setVerificationExpiresAt(LocalDateTime.parse(required(data, "expiresAt")));
            } else if (ORDER_EMAIL_TYPES.contains(type)) {
                delivery.setOrderId(required(data, "orderId"));
            } else {
                throw new IllegalArgumentException("Unsupported notification event type");
            }
            try {
                deliveries.save(delivery);
            } catch (DuplicateKeyException duplicate) {
                log.debug("Notification event already persisted: {}", eventId);
            }
        } catch (IllegalArgumentException | java.io.IOException invalid) {
            log.warn("Invalid notification event routed to dead queue: {}", invalid.getClass().getSimpleName());
            rabbitTemplate.invoke(operations -> {
                operations.send(RabbitMQConfig.EXCHANGE_NAME, "notification.dead", message);
                operations.waitForConfirmsOrDie(5000);
                return null;
            });
        }
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Missing field: " + field);
        return value;
    }

    private static String email(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.contains("@") || normalized.contains(" ")) {
            throw new IllegalArgumentException("Invalid email address");
        }
        return normalized;
    }
}
