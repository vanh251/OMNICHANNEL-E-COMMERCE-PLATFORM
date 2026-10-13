package com.example.paymentservice.dto;

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
    private Long providerOrderCode;
    private String paymentLinkId;
    private String checkoutUrl;
    private String qrCode;
    private Long expiresAt;
    private boolean refundRequired;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
