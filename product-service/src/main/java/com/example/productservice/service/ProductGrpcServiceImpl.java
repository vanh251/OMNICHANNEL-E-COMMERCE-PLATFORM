package com.example.productservice.service;

import com.example.productservice.entity.Product;
import com.example.productservice.entity.ProductVariant;
import com.example.productservice.grpc.ProductGrpcServiceGrpc;
import com.example.productservice.grpc.VerifyProductRequest;
import com.example.productservice.grpc.VerifyProductResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ProductGrpcServiceImpl extends ProductGrpcServiceGrpc.ProductGrpcServiceImplBase {

    private final MongoTemplate mongoTemplate;

    @Override
    public void verifyProductDetails(VerifyProductRequest request, StreamObserver<VerifyProductResponse> responseObserver) {
        String sku = request.getSku();
        log.info("Received gRPC request to verify product SKU: {}", sku);

        Query query = new Query(Criteria.where("variants.sku").is(sku));
        Product product = mongoTemplate.findOne(query, Product.class);

        if (product != null && product.isActive()) {
            ProductVariant targetVariant = product.getVariants().stream()
                    .filter(v -> sku.equals(v.getSku()))
                    .findFirst()
                    .orElse(null);

            if (targetVariant != null) {
                VerifyProductResponse response = VerifyProductResponse.newBuilder()
                        .setValid(true)
                        .setProductId(product.getId())
                        .setName(product.getName() + " - " + targetVariant.getName())
                        .setPrice(targetVariant.getPrice().doubleValue())
                        .setAvailableStock(targetVariant.getStock() != null ? targetVariant.getStock() : 0)
                        .setErrorMessage("")
                        .build();

                log.info("gRPC response: valid=true for SKU: {}", sku);
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }
        }

        log.warn("gRPC response: valid=false for SKU: {}", sku);
        VerifyProductResponse response = VerifyProductResponse.newBuilder()
                .setValid(false)
                .setErrorMessage("Product not found, inactive, or SKU is invalid: " + sku)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
