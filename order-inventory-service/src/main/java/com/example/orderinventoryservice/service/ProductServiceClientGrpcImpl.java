package com.example.orderinventoryservice.service;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import com.example.productservice.grpc.ProductGrpcServiceGrpc;
import com.example.productservice.grpc.VerifyProductRequest;
import com.example.productservice.grpc.VerifyProductResponse;

import java.math.BigDecimal;

@Slf4j
@Primary
@Service
public class ProductServiceClientGrpcImpl implements ProductServiceClient {

    @GrpcClient("product-service")
    private ProductGrpcServiceGrpc.ProductGrpcServiceBlockingStub productGrpcStub;

    @Override
    public ProductInfo getProductInfo(String productId, String sku) {
        log.debug("gRPC Client: fetching product info for productId={}, sku={}", productId, sku);
        try {
            VerifyProductRequest request = VerifyProductRequest.newBuilder()
                    .setSku(sku)
                    .build();

            VerifyProductResponse response = productGrpcStub.verifyProductDetails(request);

            if (response.getValid()) {
                // Return exactly the product name and price from Product Service
                return new ProductInfo(response.getName(), BigDecimal.valueOf(response.getPrice()));
            } else {
                log.warn("gRPC Client: Product not valid. Error: {}", response.getErrorMessage());
                return null;
            }
        } catch (Exception e) {
            log.error("gRPC Client: Error calling Product Service via gRPC", e);
            return null;
        }
    }
}
