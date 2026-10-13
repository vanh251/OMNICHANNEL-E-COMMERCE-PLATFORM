package com.example.orderinventoryservice.event;

import com.example.orderinventoryservice.config.RabbitMQConfig;
import com.example.orderinventoryservice.service.InventorySyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class InventorySyncConsumer {
    private final InventorySyncService inventorySyncService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.INVENTORY_SYNC_QUEUE)
    public void receive(Message message) {
        try {
            inventorySyncService.sync(objectMapper.readValue(message.getBody(), InventorySyncEvent.class));
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid catalog inventory sync event", exception);
        }
    }
}
