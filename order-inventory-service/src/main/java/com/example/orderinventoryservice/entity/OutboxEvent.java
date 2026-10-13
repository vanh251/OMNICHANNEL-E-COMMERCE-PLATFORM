package com.example.orderinventoryservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events", indexes =
        @Index(name = "idx_outbox_pending_id", columnList = "published,id"))
@Getter
@NoArgsConstructor
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String routingKey;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(nullable = false)
    private boolean published;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public OutboxEvent(String routingKey, String payload) {
        this.routingKey = routingKey;
        this.payload = payload;
    }

    public void markPublished() {
        published = true;
    }
}
