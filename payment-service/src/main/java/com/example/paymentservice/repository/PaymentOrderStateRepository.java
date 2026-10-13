package com.example.paymentservice.repository;

import com.example.paymentservice.entity.PaymentOrderState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderStateRepository extends JpaRepository<PaymentOrderState, String> {
    // PostgreSQL upsert serializes simultaneous first events without a failed transaction.
    @Modifying
    @Query(value = "INSERT INTO payment_order_state (order_id, cancelled) VALUES (:orderId, false) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void initialize(@Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PaymentOrderState s where s.orderId = :orderId")
    PaymentOrderState lockByOrderId(@Param("orderId") String orderId);
}
