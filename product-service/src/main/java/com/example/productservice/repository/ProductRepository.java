package com.example.productservice.repository;

import com.example.productservice.entity.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.Query;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findByCategoryId(String categoryId);

    List<Product> findByActiveTrue();

    boolean existsByName(String name);

    boolean existsByNameIgnoreCase(String name);

    @Query("{ 'variants.sku': ?0 }")
    Optional<Product> findByVariantSku(String sku);
}
