package com.example.paymentservice.service;

import com.example.paymentservice.dto.PaymentRequest;
import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.exception.PaymentNotFoundException;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.repository.OutboxEventRepository;
import com.example.paymentservice.entity.OutboxEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Map;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        var existing = paymentRepository.findByOrderId(request.getOrderId());
        if (existing.isPresent()) return mapToResponse(existing.get());
        log.info("Creating payment for order: {}", request.getOrderId());
        
        PaymentStatus initialStatus;
        String transactionId = null;
        
        // Mocking payment gateway logic
        if (request.getAmount().doubleValue() > 10000) {
            initialStatus = PaymentStatus.FAILED;
        } else {
            initialStatus = PaymentStatus.SUCCESS;
            transactionId = "TXN-" + UUID.randomUUID().toString();
        }

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentMethod(request.getPaymentMethod())
                .status(initialStatus)
                .transactionId(transactionId)
                .build();

        Payment saved = paymentRepository.save(payment);
        String eventType = saved.getStatus() == PaymentStatus.SUCCESS
                ? "PaymentSucceededEvent" : "PaymentFailedEvent";
        String routingKey = saved.getStatus() == PaymentStatus.SUCCESS
                ? "payment.succeeded" : "payment.failed";
        Map<String, Object> data = saved.getStatus() == PaymentStatus.SUCCESS
                ? Map.of("transactionId", saved.getTransactionId(), "orderId", saved.getOrderId(),
                        "paymentMethod", saved.getPaymentMethod(), "amountPaid", saved.getAmount(),
                        "paidAt", Instant.now().toString())
                : Map.of("transactionId", "", "orderId", saved.getOrderId(),
                        "reason", "MOCK_PAYMENT_DECLINED", "failedAt", Instant.now().toString());
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", UUID.randomUUID().toString(), "eventType", eventType,
                    "timestamp", Instant.now().toString(), "data", data));
            outboxEventRepository.save(new OutboxEvent(routingKey, payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize payment event", exception);
        }
        log.info("Payment created with ID: {}, Status: {}", saved.getId(), saved.getStatus());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for orderId: " + orderId));
        return mapToResponse(payment);
    }

    @Transactional
    public PaymentResponse refundPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));
                
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Only SUCCESS payments can be refunded");
        }
        
        payment.setStatus(PaymentStatus.REFUNDED);
        Payment updated = paymentRepository.save(payment);
        
        log.info("Payment refunded with ID: {}", updated.getId());
        return mapToResponse(updated);
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .transactionId(payment.getTransactionId())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
