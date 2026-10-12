package com.example.paymentservice.event;

import com.example.paymentservice.repository.OutboxEventRepository;
import com.example.paymentservice.entity.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.Assert;
import java.util.List;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final int batchSize;
    private final long confirmTimeoutMs;

    public OutboxPublisher(OutboxEventRepository repository, RabbitTemplate rabbitTemplate,
                           @Value("${outbox.batch-size:200}") int batchSize,
                           @Value("${outbox.confirm-timeout-ms:5000}") long confirmTimeoutMs) {
        Assert.isTrue(batchSize > 0 && batchSize <= 1000, "Outbox batch size must be between 1 and 1000");
        Assert.isTrue(confirmTimeoutMs > 0, "Outbox confirm timeout must be positive");
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.batchSize = batchSize;
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:50}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> events = repository.findPendingBatch(batchSize);
        if (events.isEmpty()) {
            return;
        }
        try {
            // All sends share one channel; await broker confirmation once for the entire batch.
            rabbitTemplate.invoke(operations -> {
                for (OutboxEvent event : events) {
                    MessageProperties properties = new MessageProperties();
                    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    operations.send("ecommerce.exchange", event.getRoutingKey(),
                            new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties));
                }
                operations.waitForConfirmsOrDie(confirmTimeoutMs);
                return null;
            });
        } catch (Exception exception) {
            // Partial sends or lost confirmations can cause redelivery; keep every event pending.
            log.warn("Outbox batch publish failed ({} events); retrying later", events.size(), exception);
            return;
        }
        events.forEach(OutboxEvent::markPublished);
    }
}
