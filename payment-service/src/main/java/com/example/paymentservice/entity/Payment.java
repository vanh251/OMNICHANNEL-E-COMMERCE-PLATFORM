package com.example.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(columnNames = "order_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    // Nullable for historical rows; rows without an owner are visible only to ADMIN.
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "transaction_id", unique = true)
    private String transactionId;

    @Column(name = "provider_order_code", unique = true)
    private Long providerOrderCode;

    @Column(name = "payment_link_id", unique = true)
    private String paymentLinkId;

    @Column(name = "checkout_url", length = 1024)
    private String checkoutUrl;

    @Column(name = "qr_code", length = 4096)
    private String qrCode;

    private Long expiresAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean refundRequired;

    private LocalDateTime providerCheckedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
