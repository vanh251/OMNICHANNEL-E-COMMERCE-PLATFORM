package com.example.identityservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalDateTime;

@Entity
@Table(name = "identity_outbox_events", indexes = @Index(name = "idx_identity_outbox_pending", columnList = "published,id"))
@Getter
public class IdentityOutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String routingKey;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(nullable = false)
    private boolean published;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected IdentityOutboxEvent() {}

    public IdentityOutboxEvent(String routingKey, String payload) {
        this.routingKey = routingKey;
        this.payload = payload;
    }

    public void markPublished() { published = true; }
}
