package com.example.productservice.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private String id;
    private String name;
    private String description;
    private String brand;
    private String categoryId;
    private String categoryName;
    private List<ProductVariantDTO> variants;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
