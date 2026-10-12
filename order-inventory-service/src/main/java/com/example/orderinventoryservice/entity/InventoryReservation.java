package com.example.orderinventoryservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "inventory_reservation", indexes =
        @Index(name = "idx_reservation_order_id", columnList = "orderId"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum ReservationStatus {
        PENDING, CONFIRMED, RELEASED
    }
}
