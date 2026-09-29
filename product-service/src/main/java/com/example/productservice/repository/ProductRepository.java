package com.example.productservice.repository;

import com.example.productservice.entity.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findByCategoryId(String categoryId);

    List<Product> findByActiveTrue();

    boolean existsByName(String name);

    boolean existsByNameIgnoreCase(String name);
}
