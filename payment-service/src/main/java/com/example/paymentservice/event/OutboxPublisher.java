package com.example.paymentservice.event;

import com.example.paymentservice.repository.OutboxEventRepository;
import com.example.paymentservice.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {
    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelayString = "${outbox.poll-ms:1000}")
    @Transactional
    public void publishPending() {
        for (OutboxEvent event : repository.findTop20ByPublishedFalseOrderByIdAsc()) {
            try {
                rabbitTemplate.invoke(operations -> {
                    MessageProperties properties = new MessageProperties();
                    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    operations.send("ecommerce.exchange", event.getRoutingKey(),
                            new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties));
                    operations.waitForConfirmsOrDie(5000);
                    return null;
                });
                event.markPublished();
            } catch (Exception exception) {
                log.warn("Outbox publish failed for id={}; retrying later", event.getId(), exception);
                break;
            }
        }
    }
}
