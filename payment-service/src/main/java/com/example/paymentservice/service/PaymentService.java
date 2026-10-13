package com.example.paymentservice.service;

import com.example.paymentservice.dto.PaymentRequest;
import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.exception.PaymentNotFoundException;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.repository.OutboxEventRepository;
import com.example.paymentservice.entity.OutboxEvent;
import com.example.paymentservice.entity.PaymentOrderState;
import com.example.paymentservice.repository.PaymentOrderStateRepository;
import com.example.paymentservice.security.JwtUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.Validator;
import com.example.paymentservice.payos.PayosGateway;
import vn.payos.model.webhooks.WebhookData;
import org.springframework.beans.factory.annotation.Value;
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
    private final PaymentOrderStateRepository orderStateRepository;
    private final Validator validator;

    @Value("${payment.provider:mock}")
    private String provider;

    @Value("${payos.link-ttl-seconds:900}")
    private long payosLinkTtl;

    @jakarta.annotation.PostConstruct
    void validateProvider() {
        if (!"mock".equals(provider) && !"payos".equals(provider)) {
            throw new IllegalArgumentException("PAYMENT_PROVIDER must be mock or payos");
        }
        if (payosLinkTtl <= 0) throw new IllegalArgumentException("payOS link TTL must be positive");
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        if (!validator.validate(request).isEmpty()) {
            throw new IllegalArgumentException("Invalid order payment data");
        }
        PaymentOrderState orderState = lockOrder(request.getOrderId());
        var existing = paymentRepository.findByOrderId(request.getOrderId());
        if (existing.isPresent()) return mapToResponse(existing.get());
        if ("payos".equals(provider)) return createPayosIntent(request, orderState);
        log.info("Creating payment for order: {}", request.getOrderId());
        
        PaymentStatus initialStatus;
        String transactionId = null;
        
        // Mocking payment gateway logic
        if (orderState.isCancelled()) {
            initialStatus = PaymentStatus.FAILED;
        } else if (request.getAmount().compareTo(new java.math.BigDecimal("10000")) > 0) {
            initialStatus = PaymentStatus.FAILED;
        } else {
            initialStatus = PaymentStatus.SUCCESS;
            transactionId = "TXN-" + UUID.randomUUID().toString();
        }

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentMethod(request.getPaymentMethod())
                .status(initialStatus)
                .transactionId(transactionId)
                .build();

        Payment saved = paymentRepository.save(payment);
        // A cancellation that arrived first prevents charging when creation is delivered later.
        if (orderState.isCancelled()) return mapToResponse(saved);
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
        publish(eventType, routingKey, data);
        log.info("Payment created with ID: {}, Status: {}", saved.getId(), saved.getStatus());
        return mapToResponse(saved);
    }

    private PaymentResponse createPayosIntent(PaymentRequest request, PaymentOrderState state) {
        // Never round the order total: payOS expects whole VND.
        String failure = "VND".equals(request.getCurrency()) ? null : "PAYOS_CURRENCY_UNSUPPORTED";
        try {
            if (request.getAmount().longValueExact() <= 0) failure = "PAYOS_AMOUNT_INVALID";
        } catch (ArithmeticException ex) {
            failure = "PAYOS_AMOUNT_INVALID";
        }
        // Stable, persisted code within JavaScript's safe integer range, independent of DB resets.
        long code = new java.security.SecureRandom().nextLong() & ((1L << 53) - 1);
        if (code == 0) code = 1;
        Payment payment = Payment.builder().orderId(request.getOrderId()).customerId(request.getCustomerId())
                .amount(request.getAmount()).currency(request.getCurrency()).paymentMethod("PAYOS")
                .status(state.isCancelled() ? PaymentStatus.FAILED : PaymentStatus.PENDING)
                .providerOrderCode(code).expiresAt(Instant.now().getEpochSecond() + payosLinkTtl).build();
        // Commit the intent before the worker makes any external request.
        Payment saved = paymentRepository.save(payment);
        // Compensate an unsupported catalog amount instead of leaving a reserved order stuck forever.
        if (failure != null && !state.isCancelled()) payosFailed(saved, failure);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(String orderId, JwtUserPrincipal principal) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for orderId: " + orderId));
        if (principal == null || (!principal.isAdmin()
                && (payment.getCustomerId() == null || !payment.getCustomerId().equals(principal.userId())))) {
            throw new AccessDeniedException("You are not authorized to access this payment");
        }
        return mapToResponse(payment);
    }

    @Transactional
    public PaymentResponse refundPayment(Long paymentId) {
        // Read only the key before locking so no stale Payment enters the persistence context.
        String orderId = paymentRepository.findOrderIdById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));
        PaymentOrderState state = lockOrder(orderId);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + paymentId));
        if (!state.isCancelled()) {
            throw new IllegalStateException("Refund requires an order cancellation");
        }
        return refund(payment);
    }

    @Transactional
    public void cancelPayment(String orderId) {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("Order ID is required");
        PaymentOrderState state = lockOrder(orderId);
        state.setCancelled(true);
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                if ("PAYOS".equals(payment.getPaymentMethod())) payment.setRefundRequired(true);
                else refund(payment);
            }
        });
    }

    private PaymentOrderState lockOrder(String orderId) {
        orderStateRepository.initialize(orderId);
        return orderStateRepository.lockByOrderId(orderId);
    }

    private PaymentResponse refund(Payment payment) {
        if (payment.getStatus() == PaymentStatus.REFUNDED) return mapToResponse(payment);
        if ("PAYOS".equals(payment.getPaymentMethod())) {
            throw new IllegalStateException("payOS refund requires external reconciliation; automatic payout is not configured");
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Only SUCCESS payments can be refunded");
        }
        
        payment.setStatus(PaymentStatus.REFUNDED);
        Payment updated = paymentRepository.save(payment);
        publish("PaymentRefundedEvent", "payment.refunded", Map.of(
                "orderId", payment.getOrderId(), "transactionId", payment.getTransactionId(),
                "amountRefunded", payment.getAmount(), "refundedAt", Instant.now().toString()));
        log.info("Payment refunded with ID: {}", updated.getId());
        return mapToResponse(updated);
    }

    private void publish(String type, String routingKey, Map<String, Object> data) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", UUID.randomUUID().toString(), "eventType", type,
                    "timestamp", Instant.now().toString(), "data", data));
            outboxEventRepository.save(new OutboxEvent(routingKey, payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize payment event", exception);
        }
    }

    public record PayosTask(Long id, Long orderCode, long amount, long expiresAt, boolean cancelled) {}

    @Transactional
    public PayosTask payosTask(Long paymentId) {
        String orderId = paymentRepository.findOrderIdById(paymentId).orElseThrow();
        PaymentOrderState state = lockOrder(orderId);
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        if (!"PAYOS".equals(payment.getPaymentMethod()) || payment.getStatus() != PaymentStatus.PENDING) return null;
        payment.setProviderCheckedAt(java.time.LocalDateTime.now());
        return new PayosTask(paymentId, payment.getProviderOrderCode(), payment.getAmount().longValueExact(),
                payment.getExpiresAt(), state.isCancelled());
    }

    @Transactional
    public boolean applyPayosLink(Long paymentId, PayosGateway.Link link) {
        String orderId = paymentRepository.findOrderIdById(paymentId).orElseThrow();
        PaymentOrderState state = lockOrder(orderId);
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        if (!"PAYOS".equals(payment.getPaymentMethod())
                || !payment.getProviderOrderCode().equals(link.orderCode())
                || link.amount() == null || payment.getAmount().compareTo(java.math.BigDecimal.valueOf(link.amount())) != 0
                || link.id() == null || link.id().isBlank()
                || (payment.getPaymentLinkId() != null && !payment.getPaymentLinkId().equals(link.id()))) {
            throw new IllegalArgumentException("payOS link does not match payment");
        }
        payment.setPaymentLinkId(link.id());
        if (link.checkoutUrl() != null) payment.setCheckoutUrl(link.checkoutUrl());
        if (link.qrCode() != null) payment.setQrCode(link.qrCode());
        if ("PAID".equals(link.status())) {
            if (link.amountPaid() == null || link.amountPaid() < payment.getAmount().longValueExact()) {
                throw new IllegalArgumentException("payOS link is not fully paid");
            }
            payosSucceeded(payment, state, link.reference() == null ? "PAYOS-" + link.id() : link.reference());
        } else if (java.util.Set.of("CANCELLED", "EXPIRED", "FAILED").contains(link.status())) {
            payosFailed(payment, "PAYOS_" + link.status());
        }
        return state.isCancelled() && !java.util.Set.of("PAID", "CANCELLED", "EXPIRED", "FAILED").contains(link.status());
    }

    @Transactional
    public void failUncreatedPayosLink(Long paymentId) {
        String orderId = paymentRepository.findOrderIdById(paymentId).orElseThrow();
        PaymentOrderState state = lockOrder(orderId);
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        if (!state.isCancelled() && payment.getExpiresAt() > Instant.now().getEpochSecond()) return;
        payosFailed(payment, state.isCancelled() ? "ORDER_CANCELLED" : "PAYOS_EXPIRED");
    }

    @Transactional
    public void applyPayosWebhook(WebhookData data) {
        // Unknown signed probe notifications are acknowledged for payOS webhook registration.
        var candidate = paymentRepository.findOrderIdByProviderOrderCode(data.getOrderCode());
        if (candidate.isEmpty()) return;
        PaymentOrderState state = lockOrder(candidate.get());
        Payment payment = paymentRepository.findByOrderId(candidate.get()).orElseThrow();
        if (!"PAYOS".equals(payment.getPaymentMethod()) || !"VND".equals(data.getCurrency())
                || data.getAmount() == null || data.getAmount() <= 0
                || data.getPaymentLinkId() == null || data.getPaymentLinkId().isBlank()
                || data.getReference() == null || data.getReference().isBlank()
                || (payment.getPaymentLinkId() != null && !payment.getPaymentLinkId().equals(data.getPaymentLinkId()))) {
            throw new IllegalArgumentException("payOS webhook does not match payment");
        }
        // Partial transfers cannot confirm an order. The worker also checks cumulative amountPaid.
        if (!"00".equals(data.getCode()) || data.getAmount() < payment.getAmount().longValueExact()) return;
        payment.setPaymentLinkId(data.getPaymentLinkId());
        payosSucceeded(payment, state, data.getReference());
    }

    private void payosSucceeded(Payment payment, PaymentOrderState state, String reference) {
        if (payment.getStatus() == PaymentStatus.REFUNDED) return;
        if (state.isCancelled()) payment.setRefundRequired(true);
        if (payment.getStatus() == PaymentStatus.SUCCESS) return;
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionId(reference);
        publish("PaymentSucceededEvent", "payment.succeeded", Map.of(
                "transactionId", reference, "orderId", payment.getOrderId(),
                "paymentMethod", "PAYOS", "amountPaid", payment.getAmount(), "paidAt", Instant.now().toString()));
    }

    private void payosFailed(Payment payment, String reason) {
        if (payment.getStatus() != PaymentStatus.PENDING) return;
        payment.setStatus(PaymentStatus.FAILED);
        publish("PaymentFailedEvent", "payment.failed", Map.of(
                "transactionId", "", "orderId", payment.getOrderId(), "reason", reason,
                "failedAt", Instant.now().toString()));
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
                .providerOrderCode(payment.getProviderOrderCode())
                .paymentLinkId(payment.getPaymentLinkId())
                .checkoutUrl(payment.getCheckoutUrl())
                .qrCode(payment.getQrCode())
                .expiresAt(payment.getExpiresAt())
                .refundRequired(payment.isRefundRequired())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
