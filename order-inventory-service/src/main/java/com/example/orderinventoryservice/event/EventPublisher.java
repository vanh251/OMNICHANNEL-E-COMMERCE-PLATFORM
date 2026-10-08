package com.example.orderinventoryservice.event;

import com.example.orderinventoryservice.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishOrderCreatedEvent(OrderCreatedEvent event) {
        log.info("Publishing OrderCreatedEvent for orderId: {}", event.getData().getOrderId());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "order.created", event);
    }
}
