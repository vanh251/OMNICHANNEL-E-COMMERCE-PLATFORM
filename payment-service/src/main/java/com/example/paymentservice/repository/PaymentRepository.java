package com.example.paymentservice.repository;

import com.example.paymentservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(String orderId);
    Optional<Payment> findByProviderOrderCode(Long providerOrderCode);

    @org.springframework.data.jpa.repository.Query("select p.orderId from Payment p where p.providerOrderCode = :code")
    Optional<String> findOrderIdByProviderOrderCode(@org.springframework.data.repository.query.Param("code") Long code);

    @org.springframework.data.jpa.repository.Query("""
            select p from Payment p where p.paymentMethod = 'PAYOS' and p.status = :status
            order by coalesce(p.providerCheckedAt, p.createdAt), p.id
            """)
    java.util.List<Payment> findPayosPending(
            @org.springframework.data.repository.query.Param("status") com.example.paymentservice.entity.PaymentStatus status,
            org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.Query("select p.orderId from Payment p where p.id = :id")
    Optional<String> findOrderIdById(@org.springframework.data.repository.query.Param("id") Long id);
}
