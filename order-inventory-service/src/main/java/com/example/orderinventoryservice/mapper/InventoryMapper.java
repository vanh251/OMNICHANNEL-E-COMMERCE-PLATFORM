package com.example.orderinventoryservice.mapper;

import com.example.orderinventoryservice.dto.InventoryResponse;
import com.example.orderinventoryservice.entity.Inventory;

public final class InventoryMapper {
    private InventoryMapper() {
    }

    public static InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(inventory.getProductId(), inventory.getSku(),
                inventory.getTotalStock(), inventory.getReservedStock(),
                inventory.getAvailableStock(), inventory.getUpdatedAt());
    }
}
