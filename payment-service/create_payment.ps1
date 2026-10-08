New-Item -Path "src\main\java\com\example\paymentservice\entity" -ItemType Directory -Force
New-Item -Path "src\main\java\com\example\paymentservice\repository" -ItemType Directory -Force
New-Item -Path "src\main\java\com\example\paymentservice\dto" -ItemType Directory -Force
New-Item -Path "src\main\java\com\example\paymentservice\exception" -ItemType Directory -Force
New-Item -Path "src\main\java\com\example\paymentservice\service" -ItemType Directory -Force
New-Item -Path "src\main\java\com\example\paymentservice\controller" -ItemType Directory -Force
New-Item -Path "src\main\resources" -ItemType Directory -Force

Set-Content -Path "src\main\java\com\example\paymentservice\entity\PaymentStatus.java" -Value "package com.example.paymentservice.entity;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED
}"

Set-Content -Path "src\main\java\com\example\paymentservice\entity\Payment.java" -Value "package com.example.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}"

Set-Content -Path "src\main\java\com\example\paymentservice\repository\PaymentRepository.java" -Value "package com.example.paymentservice.repository;

import com.example.paymentservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(String orderId);
}"

Set-Content -Path "src\main\java\com\example\paymentservice\dto\PaymentRequest.java" -Value "package com.example.paymentservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {
    @NotBlank(message = "Order ID is required")
    private String orderId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;
}"

Set-Content -Path "src\main\java\com\example\paymentservice\dto\PaymentResponse.java" -Value "package com.example.paymentservice.dto;

import com.example.paymentservice.entity.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private Long id;
    private String orderId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private PaymentStatus status;
    private String transactionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}"

Set-Content -Path "src\main\java\com\example\paymentservice\exception\PaymentNotFoundException.java" -Value "package com.example.paymentservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String message) {
        super(message);
    }
}"

Set-Content -Path "src\main\java\com\example\paymentservice\exception\GlobalExceptionHandler.java" -Value "package com.example.paymentservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(PaymentNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", HttpStatus.NOT_FOUND.getReasonPhrase());
        body.put("message", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, Object> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage()));
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        body.put("message", "Validation failed");
        body.put("details", errors);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        body.put("message", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }
}"

Set-Content -Path "src\main\java\com\example\paymentservice\service\PaymentService.java" -Value "package com.example.paymentservice.service;

import com.example.paymentservice.dto.PaymentRequest;
import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.exception.PaymentNotFoundException;
import com.example.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        log.info("Creating payment for order: {}", request.getOrderId());
        
        PaymentStatus initialStatus;
        String transactionId = null;
        
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
}"

Set-Content -Path "src\main\java\com\example\paymentservice\controller\PaymentController.java" -Value "package com.example.paymentservice.controller;

import com.example.paymentservice.dto.PaymentRequest;
import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Controller", description = "APIs for payment processing")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Create a new payment")
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.createPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get payment by Order ID")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(@PathVariable String orderId) {
        return ResponseEntity.ok(paymentService.getPaymentByOrderId(orderId));
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund a payment")
    public ResponseEntity<PaymentResponse> refundPayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.refundPayment(id));
    }
}"

Set-Content -Path "src\main\resources\application.yaml" -Value "server:
  port: ${PAYMENT_SERVICE_PORT:8084}

spring:
  application:
    name: payment-service

  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${PAYMENT_DB:payment_db}
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    open-in-view: false

  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:admin}
    password: ${RABBITMQ_PASSWORD:rabbit123}

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: always"

