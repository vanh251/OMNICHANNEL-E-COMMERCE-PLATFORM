package com.example.orderinventoryservice.dto;

import java.time.LocalDateTime;

public record InventoryResponse(
        String productId,
        String sku,
        Integer totalStock,
        Integer reservedStock,
        Integer availableStock,
        LocalDateTime updatedAt) {
}
