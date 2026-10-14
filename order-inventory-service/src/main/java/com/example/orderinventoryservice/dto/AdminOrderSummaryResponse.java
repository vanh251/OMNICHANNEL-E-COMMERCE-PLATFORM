package com.example.orderinventoryservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminOrderSummaryResponse(
        Long id,
        String orderCode,
        Long userId,
        String status,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) {}
