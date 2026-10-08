package com.example.productservice.repository;

import com.example.productservice.entity.Category;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends MongoRepository<Category, String> {

    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameIgnoreCase(String name);
}
