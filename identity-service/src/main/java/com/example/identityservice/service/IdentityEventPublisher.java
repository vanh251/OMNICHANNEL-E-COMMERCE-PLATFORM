package com.example.identityservice.service;

import com.example.identityservice.entity.IdentityOutboxEvent;
import com.example.identityservice.repository.IdentityOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class IdentityEventPublisher {
    private final IdentityOutboxRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IdentityEventPublisher(IdentityOutboxRepository repository) {
        this.repository = repository;
    }

    public void publish(String type, String routingKey, Map<String, Object> data) {
        try {
            repository.save(new IdentityOutboxEvent(routingKey, objectMapper.writeValueAsString(Map.of(
                    "eventId", UUID.randomUUID().toString(), "eventType", type,
                    "timestamp", Instant.now().toString(), "data", data))));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize identity event", exception);
        }
    }
}
