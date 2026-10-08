package com.example.orderinventoryservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Stub implementation of ProductServiceClient.
 * <p>
 * Returns default product information based on the request data.
 * This will be replaced with a real HTTP-based implementation
 * when synchronous inter-service communication is set up.
 * </p>
 */
@Slf4j
// @Service // Removed in favor of ProductServiceClientGrpcImpl
public class ProductServiceClientStub implements ProductServiceClient {

    @Override
    public ProductInfo getProductInfo(String productId, String sku) {
        log.info("Stub: fetching product info for productId={}, sku={}", productId, sku);
        // In a real implementation, this would call Product Service via HTTP/gRPC
        // For now, return a placeholder with the SKU as product name
        // and a default price. The actual price lookup will be added
        // when inter-service communication is implemented.
        return new ProductInfo("Product " + sku, new BigDecimal("100000"));
    }
}
