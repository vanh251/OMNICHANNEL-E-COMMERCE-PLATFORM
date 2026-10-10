package com.example.orderinventoryservice.event;

import com.example.orderinventoryservice.service.OrderService;
import com.example.orderinventoryservice.exception.OrderNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "order.queue")
    public void receive(Message message) throws IOException {
        JsonNode event = objectMapper.readTree(message.getBody());
        String type = event.path("eventType").asText();
        Long orderId = Long.valueOf(event.path("data").path("orderId").asText());
        try {
            if ("PaymentSucceededEvent".equals(type)) orderService.confirmPayment(orderId);
            else if ("PaymentFailedEvent".equals(type)) orderService.failPayment(orderId);
            else throw new IllegalArgumentException("Unknown payment event: " + type);
        } catch (OrderNotFoundException exception) {
            log.warn("Ignoring payment event for unknown orderId={}", orderId);
        }
    }
}
