package com.example.productservice;

import com.example.productservice.dto.CategoryRequest;
import com.example.productservice.dto.ProductRequest;
import com.example.productservice.dto.ProductVariantDTO;
import com.example.productservice.entity.Category;
import com.example.productservice.entity.Product;
import com.example.productservice.repository.CategoryRepository;
import com.example.productservice.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ProductServiceIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    // ==========================================
    // CATEGORY TESTS
    // ==========================================

    @Test
    @DisplayName("Create Category - Success (201 Created)")
    void testCreateCategorySuccess() throws Exception {
        CategoryRequest request = CategoryRequest.builder()
                .name("Cat-Smartphones")
                .description("Smartphones and accessories")
                .build();

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Cat-Smartphones")))
                .andExpect(jsonPath("$.description", is("Smartphones and accessories")))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));
    }

    @Test
    @DisplayName("Get Categories - Success (200 OK)")
    void testGetCategoriesSuccess() throws Exception {
        Category category = Category.builder()
                .name("Cat-Laptops")
                .description("High-end laptops")
                .build();
        categoryRepository.save(category);

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Cat-Laptops")));
    }

    @Test
    @DisplayName("Get Category By ID - Success (200 OK)")
    void testGetCategoryByIdSuccess() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .name("Cat-Tablets")
                .description("Apple & Android Tablets")
                .build());

        mockMvc.perform(get("/api/categories/" + category.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(category.getId())))
                .andExpect(jsonPath("$.name", is("Cat-Tablets")));
    }

    @Test
    @DisplayName("Get Category By ID - Not Found (404 Not Found)")
    void testGetCategoryByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/categories/non-existent-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Category not found with id")));
    }

    @Test
    @DisplayName("Update Category - Success (200 OK)")
    void testUpdateCategorySuccess() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .name("Cat-OldCategory")
                .description("Old description")
                .build());

        CategoryRequest updateRequest = CategoryRequest.builder()
                .name("Cat-UpdatedCategory")
                .description("Updated description")
                .build();

        mockMvc.perform(put("/api/categories/" + category.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(category.getId())))
                .andExpect(jsonPath("$.name", is("Cat-UpdatedCategory")))
                .andExpect(jsonPath("$.description", is("Updated description")));
    }

    @Test
    @DisplayName("Delete Category - Success (204 No Content)")
    void testDeleteCategorySuccess() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .name("Cat-ToDelete")
                .description("Description")
                .build());

        mockMvc.perform(delete("/api/categories/" + category.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/categories/" + category.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Create Category - Duplicate Name (400 Bad Request)")
    void testCreateDuplicateCategory() throws Exception {
        categoryRepository.save(Category.builder()
                .name("Cat-DuplicateCheck")
                .description("Headphones & Speakers")
                .build());

        CategoryRequest duplicateRequest = CategoryRequest.builder()
                .name("Cat-DuplicateCheck")
                .description("Another category with same name")
                .build();

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    // ==========================================
    // PRODUCT TESTS
    // ==========================================

    @Test
    @DisplayName("Create Product - Success (201 Created)")
    void testCreateProductSuccess() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .name("Cat-Phones")
                .description("Mobile phones")
                .build());

        ProductVariantDTO variant1 = ProductVariantDTO.builder()
                .sku("IP17-BLK-256")
                .name("Black 256GB")
                .price(new BigDecimal("25000000"))
                .oldPrice(new BigDecimal("27000000"))
                .stock(100)
                .imageUrl("https://example.com/ip17-black.jpg")
                .build();

        ProductVariantDTO variant2 = ProductVariantDTO.builder()
                .sku("IP17-WHT-256")
                .name("White 256GB")
                .price(new BigDecimal("25000000"))
                .oldPrice(new BigDecimal("27000000"))
                .stock(80)
                .imageUrl("https://example.com/ip17-white.jpg")
                .build();

        ProductRequest productRequest = ProductRequest.builder()
                .name("iPhone 17")
                .description("Apple iPhone 17")
                .brand("Apple")
                .categoryId(category.getId())
                .variants(List.of(variant1, variant2))
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("iPhone 17")))
                .andExpect(jsonPath("$.brand", is("Apple")))
                .andExpect(jsonPath("$.categoryId", is(category.getId())))
                .andExpect(jsonPath("$.categoryName", is("Cat-Phones")))
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$.variants", hasSize(2)))
                .andExpect(jsonPath("$.variants[0].sku", is("IP17-BLK-256")))
                .andExpect(jsonPath("$.variants[0].price", is(25000000)))
                .andExpect(jsonPath("$.variants[0].stock", is(100)))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));
    }

    @Test
    @DisplayName("Create Product - Invalid Category (404 Not Found)")
    void testCreateProductInvalidCategory() throws Exception {
        ProductVariantDTO variant = ProductVariantDTO.builder()
                .sku("IP17-BLK-256")
                .name("Black 256GB")
                .price(new BigDecimal("25000000"))
                .stock(100)
                .build();

        ProductRequest productRequest = ProductRequest.builder()
                .name("iPhone 17")
                .categoryId("non-existent-cat-id")
                .variants(List.of(variant))
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Category not found with id")));
    }

    @Test
    @DisplayName("Get Products - Success (200 OK)")
    void testGetProductsSuccess() throws Exception {
        Category category = categoryRepository.save(Category.builder().name("Cat-Monitors").build());

        ProductVariantDTO variant = ProductVariantDTO.builder()
                .sku("MON-4K-27")
                .name("4K 27 inch")
                .price(new BigDecimal("9000000"))
                .stock(30)
                .build();

        ProductRequest request = ProductRequest.builder()
                .name("Dell UltraSharp")
                .brand("Dell")
                .categoryId(category.getId())
                .variants(List.of(variant))
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Dell UltraSharp")));
    }

    @Test
    @DisplayName("Get Product By ID - Success (200 OK)")
    void testGetProductByIdSuccess() throws Exception {
        Category category = categoryRepository.save(Category.builder().name("Cat-Keyboards").build());

        ProductVariantDTO variant = ProductVariantDTO.builder()
                .sku("KEY-MECH-RED")
                .name("Red Switch")
                .price(new BigDecimal("2000000"))
                .stock(50)
                .build();

        ProductRequest request = ProductRequest.builder()
                .name("Mechanical Keyboard")
                .brand("Keychron")
                .categoryId(category.getId())
                .variants(List.of(variant))
                .build();

        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        assertNotNull(root.get("id"));
        String productId = root.get("id").asText();

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(productId)))
                .andExpect(jsonPath("$.name", is("Mechanical Keyboard")));
    }

    @Test
    @DisplayName("Get Products By Category - Success (200 OK)")
    void testGetProductsByCategorySuccess() throws Exception {
        Category category = categoryRepository.save(Category.builder().name("Cat-Wearables").build());

        ProductVariantDTO variant = ProductVariantDTO.builder()
                .sku("WATCH-ULTRA")
                .name("Titanium")
                .price(new BigDecimal("19000000"))
                .stock(20)
                .build();

        ProductRequest request = ProductRequest.builder()
                .name("Smart Watch")
                .brand("Apple")
                .categoryId(category.getId())
                .variants(List.of(variant))
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products/category/" + category.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Smart Watch")));
    }

    @Test
    @DisplayName("Update Product - Success (200 OK)")
    void testUpdateProductSuccess() throws Exception {
        Category category = categoryRepository.save(Category.builder().name("Cat-AudioUpdate").build());

        ProductVariantDTO variant = ProductVariantDTO.builder()
                .sku("SONY-XM5")
                .name("Black")
                .price(new BigDecimal("8000000"))
                .stock(40)
                .build();

        ProductRequest request = ProductRequest.builder()
                .name("Sony WH-1000XM4")
                .brand("Sony")
                .categoryId(category.getId())
                .variants(List.of(variant))
                .build();

        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        assertNotNull(root.get("id"));
        String productId = root.get("id").asText();

        // Update product name to WH-1000XM5
        request.setName("Sony WH-1000XM5");
        mockMvc.perform(put("/api/products/" + productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(productId)))
                .andExpect(jsonPath("$.name", is("Sony WH-1000XM5")));
    }

    @Test
    @DisplayName("Delete Product - Success (204 No Content)")
    void testDeleteProductSuccess() throws Exception {
        Category category = categoryRepository.save(Category.builder().name("Cat-Accessories").build());

        ProductVariantDTO variant = ProductVariantDTO.builder()
                .sku("CABLE-USBC")
                .name("Type-C to Type-C 1m")
                .price(new BigDecimal("200000"))
                .stock(200)
                .build();

        ProductRequest request = ProductRequest.builder()
                .name("USB-C Cable")
                .categoryId(category.getId())
                .variants(List.of(variant))
                .build();

        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        assertNotNull(root.get("id"));
        String productId = root.get("id").asText();

        mockMvc.perform(delete("/api/products/" + productId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isNotFound());
    }

    // ==========================================
    // VALIDATION TESTS
    // ==========================================

    @Test
    @DisplayName("Validation - Empty Category Name (400 Bad Request)")
    void testValidationEmptyCategoryName() throws Exception {
        CategoryRequest request = CategoryRequest.builder()
                .name("")
                .build();

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Validation failed")));
    }

    @Test
    @DisplayName("Validation - Empty Product Name (400 Bad Request)")
    void testValidationEmptyProductName() throws Exception {
        ProductVariantDTO variant = ProductVariantDTO.builder()
                .sku("SKU-1")
                .name("V1")
                .price(new BigDecimal("100000"))
                .stock(10)
                .build();

        ProductRequest request = ProductRequest.builder()
                .name("")
                .categoryId("valid-category-id")
                .variants(List.of(variant))
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Validation failed")));
    }

    @Test
    @DisplayName("Validation - Empty Category ID (400 Bad Request)")
    void testValidationEmptyCategoryId() throws Exception {
        ProductVariantDTO variant = ProductVariantDTO.builder()
                .sku("SKU-1")
                .name("V1")
                .price(new BigDecimal("100000"))
                .stock(10)
                .build();

        ProductRequest request = ProductRequest.builder()
                .name("Valid Product")
                .categoryId("")
                .variants(List.of(variant))
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Validation failed")));
    }

    @Test
    @DisplayName("Validation - Empty SKU (400 Bad Request)")
    void testValidationEmptySku() throws Exception {
        ProductVariantDTO variant = ProductVariantDTO.builder()
                .sku("")
                .name("V1")
                .price(new BigDecimal("100000"))
                .stock(10)
                .build();

        ProductRequest request = ProductRequest.builder()
                .name("Valid Product")
                .categoryId("cat-123")
                .variants(List.of(variant))
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Validation failed")));
    }

    @Test
    @DisplayName("Validation - Negative Price (400 Bad Request)")
    void testValidationNegativePrice() throws Exception {
        ProductVariantDTO variant = ProductVariantDTO.builder()
                .sku("SKU-NEG")
                .name("V1")
                .price(new BigDecimal("-1000"))
                .stock(10)
                .build();

        ProductRequest request = ProductRequest.builder()
                .name("Valid Product")
                .categoryId("cat-123")
                .variants(List.of(variant))
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Validation failed")));
    }

    @Test
    @DisplayName("Validation - Negative Stock (400 Bad Request)")
    void testValidationNegativeStock() throws Exception {
        ProductVariantDTO variant = ProductVariantDTO.builder()
                .sku("SKU-STOCK")
                .name("V1")
                .price(new BigDecimal("1000"))
                .stock(-5)
                .build();

        ProductRequest request = ProductRequest.builder()
                .name("Valid Product")
                .categoryId("cat-123")
                .variants(List.of(variant))
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Validation failed")));
    }
}
