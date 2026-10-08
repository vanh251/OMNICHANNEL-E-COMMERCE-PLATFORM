package com.example.notificationservice.consumer;

import com.example.notificationservice.config.RabbitMQConfig;
import com.example.notificationservice.dto.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationConsumer {

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void consumeOrderCreatedEvent(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for orderId: {}", event.getData().getOrderId());
        
        // Mock Email as per Day 8 requirements
        System.out.println("==================================================");
        System.out.println("[EMAIL]");
        System.out.println("Your order #" + event.getData().getOrderId() + " has been created.");
        System.out.println("==================================================");
    }
}
