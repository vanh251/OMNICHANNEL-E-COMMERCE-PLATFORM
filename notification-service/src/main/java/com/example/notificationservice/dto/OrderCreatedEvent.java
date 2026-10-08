package com.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private String eventId;
    private String eventType;
    private String timestamp;
    private OrderCreatedEventData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCreatedEventData {
        private String orderId;
        private String customerId;
        private BigDecimal totalAmount;
        private String currency;
        private List<OrderItemData> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemData {
        private String productId;
        private String sku;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
