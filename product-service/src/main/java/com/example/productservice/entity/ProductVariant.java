package com.example.productservice.entity;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariant {

    private String sku;
    private String name;
    private BigDecimal price;
    private BigDecimal oldPrice;
    private Integer stock;
    private String imageUrl;
}
