package com.example.productservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
// Legacy catalog rows can contain duplicate SKUs. Enforce uniqueness for newly
// created products while those rows are reconciled without deleting user data.
@CompoundIndex(name = "unique_variant_sku", def = "{'variants.sku': 1}", unique = true,
        partialFilter = "{'skuIndexEligible': true}")
public class Product {

    @Id
    private String id;

    private String name;

    private String description;

    private String brand;

    private String categoryId;

    private String categoryName;

    private List<ProductVariant> variants;

    private boolean active;

    private boolean skuIndexEligible;

    // Embedded outbox: saved atomically with the product in one MongoDB document.
    private String inventorySyncEventId;
    private boolean inventorySyncPublished;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
