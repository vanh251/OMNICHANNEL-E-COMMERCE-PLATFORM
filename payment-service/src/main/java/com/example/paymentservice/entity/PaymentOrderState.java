package com.example.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Durable cancellation marker and lock target, including orders not yet charged. */
@Entity
@Table(name = "payment_order_state")
@Getter
@Setter
@NoArgsConstructor
public class PaymentOrderState {
    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(nullable = false)
    private boolean cancelled;
}
