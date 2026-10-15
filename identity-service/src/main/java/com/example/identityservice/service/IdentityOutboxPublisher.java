package com.example.identityservice.service;

import com.example.identityservice.config.IdentityRabbitConfig;
import com.example.identityservice.entity.IdentityOutboxEvent;
import com.example.identityservice.repository.IdentityOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true", matchIfMissing = true)
public class IdentityOutboxPublisher {
    private final IdentityOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelayString = "${outbox.poll-ms:500}")
    @Transactional
    public void publishPending() {
        List<IdentityOutboxEvent> events = repository.findPending(PageRequest.of(0, 100));
        if (events.isEmpty()) return;
        try {
            rabbitTemplate.invoke(operations -> {
                for (IdentityOutboxEvent event : events) {
                    MessageProperties properties = new MessageProperties();
                    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    operations.send(IdentityRabbitConfig.EXCHANGE, event.getRoutingKey(),
                            new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties));
                }
                operations.waitForConfirmsOrDie(5000);
                return null;
            });
            events.forEach(IdentityOutboxEvent::markPublished);
        } catch (Exception exception) {
            log.warn("Identity Outbox publish failed; retrying {} events", events.size(), exception);
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredVerificationLinks() {
        repository.deleteByPublishedTrueAndRoutingKeyAndCreatedAtBefore(
                "identity.email.verification.requested", LocalDateTime.now().minusDays(2));
    }
}
