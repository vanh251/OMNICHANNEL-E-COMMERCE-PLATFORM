package com.example.productservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
