package com.example.productservice.service;

import com.example.productservice.dto.ProductRequest;
import com.example.productservice.dto.ProductResponse;
import com.example.productservice.dto.ProductVariantDTO;
import com.example.productservice.entity.Category;
import com.example.productservice.entity.Product;
import com.example.productservice.entity.ProductVariant;
import com.example.productservice.exception.ResourceNotFoundException;
import com.example.productservice.repository.CategoryRepository;
import com.example.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductResponse createProduct(ProductRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        LocalDateTime now = LocalDateTime.now();
        List<ProductVariant> variants = request.getVariants().stream()
                .map(this::mapToVariantEntity)
                .collect(Collectors.toList());

        Product product = Product.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .brand(request.getBrand())
                .categoryId(category.getId())
                .categoryName(category.getName())
                .variants(variants)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Product saved = productRepository.save(product);
        log.info("Created product with id: {}", saved.getId());
        return mapToResponse(saved);
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public ProductResponse getProductById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return mapToResponse(product);
    }

    public List<ProductResponse> getProductsByCategory(String categoryId) {
        return productRepository.findByCategoryId(categoryId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public ProductResponse updateProduct(String id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        List<ProductVariant> variants = request.getVariants().stream()
                .map(this::mapToVariantEntity)
                .collect(Collectors.toList());

        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setBrand(request.getBrand());
        product.setCategoryId(category.getId());
        product.setCategoryName(category.getName());
        product.setVariants(variants);
        product.setUpdatedAt(LocalDateTime.now());

        Product updated = productRepository.save(product);
        log.info("Updated product with id: {}", updated.getId());
        return mapToResponse(updated);
    }

    public void deleteProduct(String id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
        log.info("Deleted product with id: {}", id);
    }

    private ProductVariant mapToVariantEntity(ProductVariantDTO dto) {
        return ProductVariant.builder()
                .sku(dto.getSku())
                .name(dto.getName())
                .price(dto.getPrice())
                .oldPrice(dto.getOldPrice())
                .stock(dto.getStock())
                .imageUrl(dto.getImageUrl())
                .build();
    }

    private ProductVariantDTO mapToVariantDTO(ProductVariant entity) {
        return ProductVariantDTO.builder()
                .sku(entity.getSku())
                .name(entity.getName())
                .price(entity.getPrice())
                .oldPrice(entity.getOldPrice())
                .stock(entity.getStock())
                .imageUrl(entity.getImageUrl())
                .build();
    }

    private ProductResponse mapToResponse(Product product) {
        List<ProductVariantDTO> variantDTOs = product.getVariants() != null
                ? product.getVariants().stream().map(this::mapToVariantDTO).collect(Collectors.toList())
                : List.of();

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .brand(product.getBrand())
                .categoryId(product.getCategoryId())
                .categoryName(product.getCategoryName())
                .variants(variantDTOs)
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
