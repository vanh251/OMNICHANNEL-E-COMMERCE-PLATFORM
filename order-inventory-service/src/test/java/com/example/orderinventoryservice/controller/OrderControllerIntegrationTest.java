package com.example.orderinventoryservice.controller;

import com.example.orderinventoryservice.dto.CreateOrderRequest;
import com.example.orderinventoryservice.dto.OrderItemRequest;
import com.example.orderinventoryservice.dto.OrderResponse;
import com.example.orderinventoryservice.entity.Order;
import com.example.orderinventoryservice.entity.OrderItem;
import com.example.orderinventoryservice.entity.OrderStatus;
import com.example.orderinventoryservice.entity.OrderStatusHistory;
import com.example.orderinventoryservice.repository.OrderItemRepository;
import com.example.orderinventoryservice.repository.OrderRepository;
import com.example.orderinventoryservice.repository.OrderStatusHistoryRepository;
import com.example.orderinventoryservice.repository.InventoryRepository;
import com.example.orderinventoryservice.entity.Inventory;
import com.example.orderinventoryservice.security.JwtUserPrincipal;
import com.example.orderinventoryservice.service.ProductServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @MockitoBean
    private ProductServiceClient productServiceClient;

    @MockitoBean
    private com.example.orderinventoryservice.event.EventPublisher eventPublisher;

    private static final Long USER_ID = 100L;
    private static final String USER_EMAIL = "testuser@example.com";

    private UsernamePasswordAuthenticationToken createAuth(Long userId, String email) {
        JwtUserPrincipal principal = new JwtUserPrincipal(userId, email, "USER");
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderStatusHistoryRepository.deleteAll();
        orderRepository.deleteAll();
        inventoryRepository.deleteAll();

        // Create dummy inventory for tests
        inventoryRepository.save(Inventory.builder()
                .productId("1")
                .sku("IP17-BLK-256")
                .totalStock(100)
                .reservedStock(0)
                .availableStock(100)
                .build());

        inventoryRepository.save(Inventory.builder()
                .productId("2")
                .sku("IP17-WHT-128")
                .totalStock(50)
                .reservedStock(0)
                .availableStock(50)
                .build());
                
        inventoryRepository.save(Inventory.builder()
                .productId("3")
                .sku("SKU-001")
                .totalStock(10)
                .reservedStock(0)
                .availableStock(10)
                .build());
                
        inventoryRepository.save(Inventory.builder()
                .productId("4")
                .sku("SKU-000")
                .totalStock(10)
                .reservedStock(0)
                .availableStock(10)
                .build());
                
        // Mock gRPC client behavior
        org.mockito.Mockito.when(productServiceClient.getProductInfo(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    String sku = invocation.getArgument(1);
                    return new ProductServiceClient.ProductInfo("Product " + sku, java.math.BigDecimal.valueOf(100000));
                });
    }

    @Test
    @DisplayName("POST /api/orders - Create Order Success (201)")
    void createOrder_success() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId("product-001")
                                .sku("IP17-BLK-256")
                                .quantity(2)
                                .build()
                ))
                .shippingAddress("Hanoi, Vietnam")
                .note("Call before delivery")
                .build();

        mockMvc.perform(post("/api/orders")
                        .with(authentication(createAuth(USER_ID, USER_EMAIL)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.orderCode").value(startsWith("ORD-")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.statusHistories", hasSize(1)))
                .andExpect(jsonPath("$.statusHistories[0].oldStatus").isEmpty())
                .andExpect(jsonPath("$.statusHistories[0].newStatus").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/orders - Empty Items (400)")
    void createOrder_emptyItems() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(Collections.emptyList())
                .shippingAddress("Hanoi, Vietnam")
                .build();

        mockMvc.perform(post("/api/orders")
                        .with(authentication(createAuth(USER_ID, USER_EMAIL)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/orders - Invalid Request (400)")
    void createOrder_invalidRequest() throws Exception {
        // quantity <= 0
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId("product-001")
                                .sku("IP17-BLK-256")
                                .quantity(0)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/orders")
                        .with(authentication(createAuth(USER_ID, USER_EMAIL)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/orders - No Auth (401)")
    void createOrder_noAuth() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId("product-001")
                                .sku("IP17-BLK-256")
                                .quantity(2)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/orders/{id} - Success (200)")
    void getOrderById_success() throws Exception {
        // Create an order first
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId("product-001")
                                .sku("IP17-BLK-256")
                                .quantity(1)
                                .build()
                ))
                .shippingAddress("HCMC, Vietnam")
                .build();

        String responseBody = mockMvc.perform(post("/api/orders")
                        .with(authentication(createAuth(USER_ID, USER_EMAIL)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        OrderResponse created = objectMapper.readValue(responseBody, OrderResponse.class);

        // Get it
        mockMvc.perform(get("/api/orders/{id}", created.getId())
                        .with(authentication(createAuth(USER_ID, USER_EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.orderCode").value(created.getOrderCode()));
    }

    @Test
    @DisplayName("GET /api/orders/{id} - Not Found (404)")
    void getOrderById_notFound() throws Exception {
        mockMvc.perform(get("/api/orders/{id}", 99999L)
                        .with(authentication(createAuth(USER_ID, USER_EMAIL))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/orders/{id} - Unauthorized Access (403)")
    void getOrderById_unauthorizedAccess() throws Exception {
        // Create order as USER_ID
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId("product-001")
                                .sku("SKU-001")
                                .quantity(1)
                                .build()
                ))
                .build();

        String responseBody = mockMvc.perform(post("/api/orders")
                        .with(authentication(createAuth(USER_ID, USER_EMAIL)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        OrderResponse created = objectMapper.readValue(responseBody, OrderResponse.class);

        // Try to access as a different user
        mockMvc.perform(get("/api/orders/{id}", created.getId())
                        .with(authentication(createAuth(200L, "other@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/orders/my-orders - Success (200)")
    void getMyOrders_success() throws Exception {
        // Create 2 orders
        for (int i = 0; i < 2; i++) {
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .items(List.of(
                            OrderItemRequest.builder()
                                    .productId("product-00" + i)
                                    .sku("SKU-00" + i)
                                    .quantity(1)
                                    .build()
                    ))
                    .build();

            mockMvc.perform(post("/api/orders")
                            .with(authentication(createAuth(USER_ID, USER_EMAIL)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/orders/my-orders")
                        .with(authentication(createAuth(USER_ID, USER_EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/orders/my-orders - User has no orders (200)")
    void getMyOrders_empty() throws Exception {
        mockMvc.perform(get("/api/orders/my-orders")
                        .with(authentication(createAuth(999L, "noorders@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("POST /api/orders/{id}/cancel - PENDING Success (200)")
    void cancelOrder_pendingSuccess() throws Exception {
        // Create order
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId("product-001")
                                .sku("SKU-001")
                                .quantity(1)
                                .build()
                ))
                .build();

        String responseBody = mockMvc.perform(post("/api/orders")
                        .with(authentication(createAuth(USER_ID, USER_EMAIL)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        OrderResponse created = objectMapper.readValue(responseBody, OrderResponse.class);

        // Cancel it
        mockMvc.perform(post("/api/orders/{id}/cancel", created.getId())
                        .with(authentication(createAuth(USER_ID, USER_EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.statusHistories", hasSize(2)));
    }

    @Test
    @DisplayName("POST /api/orders/{id}/cancel - Already cancelled (400)")
    void cancelOrder_alreadyCancelled() throws Exception {
        // Create and cancel
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId("product-001")
                                .sku("SKU-001")
                                .quantity(1)
                                .build()
                ))
                .build();

        String responseBody = mockMvc.perform(post("/api/orders")
                        .with(authentication(createAuth(USER_ID, USER_EMAIL)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        OrderResponse created = objectMapper.readValue(responseBody, OrderResponse.class);

        mockMvc.perform(post("/api/orders/{id}/cancel", created.getId())
                        .with(authentication(createAuth(USER_ID, USER_EMAIL))))
                .andExpect(status().isOk());

        // Cancel again should fail
        mockMvc.perform(post("/api/orders/{id}/cancel", created.getId())
                        .with(authentication(createAuth(USER_ID, USER_EMAIL))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/orders/{id}/cancel - Other user order (403)")
    void cancelOrder_otherUserFail() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId("product-001")
                                .sku("SKU-001")
                                .quantity(1)
                                .build()
                ))
                .build();

        String responseBody = mockMvc.perform(post("/api/orders")
                        .with(authentication(createAuth(USER_ID, USER_EMAIL)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        OrderResponse created = objectMapper.readValue(responseBody, OrderResponse.class);

        mockMvc.perform(post("/api/orders/{id}/cancel", created.getId())
                        .with(authentication(createAuth(200L, "other@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Database: order, items, history created correctly")
    void verifyDatabaseRecords() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId("product-001")
                                .sku("IP17-BLK-256")
                                .quantity(2)
                                .build(),
                        OrderItemRequest.builder()
                                .productId("product-002")
                                .sku("IP17-WHT-128")
                                .quantity(1)
                                .build()
                ))
                .shippingAddress("Hanoi, Vietnam")
                .note("Test note")
                .build();

        String responseBody = mockMvc.perform(post("/api/orders")
                        .with(authentication(createAuth(USER_ID, USER_EMAIL)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        OrderResponse created = objectMapper.readValue(responseBody, OrderResponse.class);

        // Verify database
        Order dbOrder = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(dbOrder.getUserId()).isEqualTo(USER_ID);
        assertThat(dbOrder.getStatus()).isEqualTo(OrderStatus.PENDING);

        List<OrderItem> items = orderItemRepository.findByOrderId(created.getId());
        assertThat(items).hasSize(2);

        List<OrderStatusHistory> histories = orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(created.getId());
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getNewStatus()).isEqualTo(OrderStatus.PENDING);
    }
}
