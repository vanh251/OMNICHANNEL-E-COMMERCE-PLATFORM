package com.example.notificationservice.consumer;

import com.example.notificationservice.config.RabbitMQConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationConsumer {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void consumeEvent(Message message) throws IOException {
        JsonNode event = objectMapper.readTree(message.getBody());
        String orderId = event.path("data").path("orderId").asText();
        String text = switch (event.path("eventType").asText()) {
            case "OrderCreatedEvent" -> "Your order #" + orderId + " has been created.";
            case "PaymentSucceededEvent" -> "Payment succeeded for order #" + orderId + ".";
            case "PaymentFailedEvent" -> "Payment failed for order #" + orderId + ".";
            case "PaymentRefundedEvent" -> "Payment refunded for order #" + orderId + ".";
            case "OrderCancelledEvent" -> "Your order #" + orderId + " has been cancelled.";
            default -> throw new IllegalArgumentException("Unknown notification event");
        };
        log.info("[EMAIL] {}", text);
    }
}
