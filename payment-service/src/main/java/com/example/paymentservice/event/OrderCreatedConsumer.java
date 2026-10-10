package com.example.paymentservice.event;

import com.example.paymentservice.dto.PaymentRequest;
import com.example.paymentservice.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class OrderCreatedConsumer {
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "payment.queue")
    public void receive(Message message) throws IOException {
        JsonNode event = objectMapper.readTree(message.getBody());
        if (!"OrderCreatedEvent".equals(event.path("eventType").asText()))
            throw new IllegalArgumentException("Unknown order event");
        JsonNode data = event.path("data");
        PaymentRequest request = new PaymentRequest();
        request.setOrderId(data.path("orderId").asText());
        request.setAmount(new BigDecimal(data.path("totalAmount").asText()));
        request.setCurrency(data.path("currency").asText());
        request.setPaymentMethod("MOCK");
        paymentService.createPayment(request);
    }
}
