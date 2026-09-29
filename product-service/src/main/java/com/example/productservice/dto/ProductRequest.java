package com.example.productservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    private String brand;

    @NotBlank(message = "Category ID is required")
    private String categoryId;

    @NotEmpty(message = "Product must have at least one variant")
    private List<@Valid ProductVariantDTO> variants;
}
