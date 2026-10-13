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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MongoTemplate mongoTemplate;

    public ProductResponse createProduct(ProductRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        validateSkus(request.getVariants(), null);
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
                .active(false)
                .skuIndexEligible(true)
                .inventorySyncEventId(UUID.randomUUID().toString())
                .inventorySyncPublished(false)
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

        validateSkus(request.getVariants(), id);
        Set<String> existingSkus = product.getVariants().stream()
                .map(ProductVariant::getSku).collect(Collectors.toSet());
        for (ProductVariantDTO variant : request.getVariants()) {
            product.getVariants().stream()
                    .filter(existing -> existing.getSku().equals(variant.getSku().trim()))
                    .findFirst()
                    .ifPresent(existing -> {
                        if (!Objects.equals(existing.getStock(), variant.getStock())) {
                            throw new IllegalArgumentException(
                                    "Existing SKU stock is managed by inventory service: " + existing.getSku());
                        }
                    });
        }
        boolean addedSku = request.getVariants().stream()
                .anyMatch(variant -> !existingSkus.contains(variant.getSku()));

        List<ProductVariant> variants = request.getVariants().stream()
                .map(this::mapToVariantEntity)
                .collect(Collectors.toList());

        Update changes = new Update()
                .set("name", request.getName().trim())
                .set("description", request.getDescription())
                .set("brand", request.getBrand())
                .set("categoryId", category.getId())
                .set("categoryName", category.getName())
                .set("variants", variants)
                .set("updatedAt", LocalDateTime.now());
        if (addedSku) {
            changes.set("active", false)
                    .set("inventorySyncEventId", UUID.randomUUID().toString())
                    .set("inventorySyncPublished", false);
        }
        Product updated = mongoTemplate.findAndModify(Query.query(Criteria.where("id").is(id)),
                changes, FindAndModifyOptions.options().returnNew(true), Product.class);
        if (updated == null) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
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
                .sku(dto.getSku().trim())
                .name(dto.getName())
                .price(dto.getPrice())
                .oldPrice(dto.getOldPrice())
                .stock(dto.getStock())
                .imageUrl(dto.getImageUrl())
                .build();
    }

    private void validateSkus(List<ProductVariantDTO> variants, String currentProductId) {
        Set<String> skus = new HashSet<>();
        for (ProductVariantDTO variant : variants) {
            String sku = variant.getSku().trim();
            if (!skus.add(sku)) {
                throw new IllegalArgumentException("Duplicate SKU in product: " + sku);
            }
            productRepository.findByVariantSku(sku).ifPresent(owner -> {
                if (!owner.getId().equals(currentProductId)) {
                    throw new IllegalArgumentException("SKU already belongs to another product: " + sku);
                }
            });
        }
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
