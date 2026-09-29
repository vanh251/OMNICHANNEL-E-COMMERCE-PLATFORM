package com.example.orderinventoryservice.service;

import java.math.BigDecimal;

/**
 * Abstraction for resolving product information from Product Service.
 * <p>
 * In Day 5, this is implemented as a stub that returns data from the order request.
 * When inter-service communication is implemented (e.g., REST client / Feign / gRPC),
 * a real implementation will call Product Service to fetch product name and price by productId + sku.
 * </p>
 */
public interface ProductServiceClient {

    /**
     * Fetch product information by product ID and SKU.
     *
     * @param productId the product identifier
     * @param sku       the SKU identifier
     * @return product info containing name and unit price, or null if not found
     */
    ProductInfo getProductInfo(String productId, String sku);

    record ProductInfo(String productName, BigDecimal unitPrice) {
    }
}
