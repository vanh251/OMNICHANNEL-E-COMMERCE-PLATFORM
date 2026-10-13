package com.example.productservice.event;

import java.util.List;

public record InventorySyncEvent(String eventId, String eventType, String productId,
                                 List<Variant> variants) {
    public record Variant(String sku, int initialStock) {}
}
