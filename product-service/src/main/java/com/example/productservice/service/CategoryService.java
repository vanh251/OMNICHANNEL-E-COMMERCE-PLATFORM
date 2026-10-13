package com.example.productservice.service;

import com.example.productservice.dto.CategoryRequest;
import com.example.productservice.dto.CategoryResponse;
import com.example.productservice.entity.Category;
import com.example.productservice.exception.DuplicateResourceException;
import com.example.productservice.exception.ResourceNotFoundException;
import com.example.productservice.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final MongoTemplate mongoTemplate;

    public CategoryResponse createCategory(CategoryRequest request) {
        String trimmedName = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new DuplicateResourceException("Category name already exists: " + trimmedName);
        }

        LocalDateTime now = LocalDateTime.now();
        Category category = Category.builder()
                .name(trimmedName)
                .description(request.getDescription())
                .createdAt(now)
                .updatedAt(now)
                .build();

        Category saved = categoryRepository.save(category);
        log.info("Created category with id: {}", saved.getId());
        return mapToResponse(saved);
    }

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public CategoryResponse getCategoryById(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
        return mapToResponse(category);
    }

    public CategoryResponse updateCategory(String id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        String trimmedName = request.getName().trim();
        if (!category.getName().equalsIgnoreCase(trimmedName) && categoryRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new DuplicateResourceException("Category name already exists: " + trimmedName);
        }

        String oldName = category.getName();
        category.setName(trimmedName);
        category.setDescription(request.getDescription());
        category.setUpdatedAt(LocalDateTime.now());

        Category updated = categoryRepository.save(category);
        log.info("Updated category with id: {}", updated.getId());

        // Sync categoryName in products if name changed
        if (!oldName.equals(trimmedName)) {
            long count = mongoTemplate.updateMulti(Query.query(Criteria.where("categoryId").is(id)),
                    new Update().set("categoryName", trimmedName).set("updatedAt", LocalDateTime.now()),
                    com.example.productservice.entity.Product.class).getModifiedCount();
            log.info("Updated category name in {} products", count);
        }

        return mapToResponse(updated);
    }

    public void deleteCategory(String id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category not found with id: " + id);
        }
        categoryRepository.deleteById(id);
        log.info("Deleted category with id: {}", id);
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
