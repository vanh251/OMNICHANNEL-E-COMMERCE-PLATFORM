package com.example.orderinventoryservice.service;

import com.example.orderinventoryservice.entity.Inventory;
import com.example.orderinventoryservice.event.EventPublisher;
import com.example.orderinventoryservice.event.InventorySyncEvent;
import com.example.orderinventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventorySyncService {
    private final InventoryRepository inventoryRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public void sync(InventorySyncEvent event) {
        if (event == null || !"ProductInventorySyncRequested".equals(event.eventType())
                || event.eventId() == null || event.eventId().isBlank()
                || event.productId() == null || event.productId().isBlank()
                || event.variants() == null || event.variants().isEmpty()) {
            throw new IllegalArgumentException("Invalid inventory sync event");
        }
        for (InventorySyncEvent.Variant variant : event.variants()) {
            if (variant == null || variant.sku() == null || variant.sku().isBlank() || variant.initialStock() < 0) {
                throw new IllegalArgumentException("Invalid inventory sync variant");
            }
            Inventory existing = inventoryRepository.findBySku(variant.sku()).orElse(null);
            if (existing != null) {
                if (!existing.getProductId().equals(event.productId())) {
                    throw new IllegalStateException("SKU belongs to another product: " + variant.sku());
                }
                // Never overwrite stock managed by reservations, releases, and confirmations.
                continue;
            }
            inventoryRepository.saveAndFlush(Inventory.builder()
                    .productId(event.productId())
                    .sku(variant.sku())
                    .totalStock(variant.initialStock())
                    .reservedStock(0)
                    .availableStock(variant.initialStock())
                    .build());
        }
        eventPublisher.publishInventorySyncedEvent(new InventorySyncedEvent(
                event.eventId(), "ProductInventorySynced", event.productId()));
    }

    public record InventorySyncedEvent(String eventId, String eventType, String productId) {}
}
