package com.example.orderinventoryservice.controller;

import com.example.orderinventoryservice.PostgresTestDatabase;
import com.example.orderinventoryservice.entity.Inventory;
import com.example.orderinventoryservice.repository.InventoryRepository;
import com.example.orderinventoryservice.repository.InventoryReservationRepository;
import com.example.orderinventoryservice.service.ProductServiceClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired InventoryRepository inventories;
    @Autowired InventoryReservationRepository reservations;
    @MockitoBean ProductServiceClient productServiceClient;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.configure(registry);
    }

    @BeforeEach
    void setup() {
        reservations.deleteAll();
        inventories.deleteAll();
        inventories.save(Inventory.builder().productId("product-1").sku("SKU-A")
                .totalStock(10).reservedStock(3).availableStock(7).build());
        inventories.save(Inventory.builder().productId("product-2").sku("SKU-B")
                .totalStock(5).reservedStock(0).availableStock(5).build());
    }

    @Test
    void readsReturnCurrentQuantitiesWithPaginationAndProductFilter() throws Exception {
        mvc.perform(get("/api/inventory").header("Authorization", token("ADMIN")).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("SKU-A"));
        mvc.perform(get("/api/inventory").header("Authorization", token("ADMIN"))
                        .param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].sku").value("SKU-B"));
        mvc.perform(get("/api/inventory").header("Authorization", token("ADMIN"))
                        .param("productId", "product-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].productId").value("product-2"));
        mvc.perform(get("/api/inventory").header("Authorization", token("ADMIN"))
                        .param("productId", "unknown"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/inventory/SKU-A").header("Authorization", token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("product-1"))
                .andExpect(jsonPath("$.totalStock").value(10))
                .andExpect(jsonPath("$.reservedStock").value(3))
                .andExpect(jsonPath("$.availableStock").value(7))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void receivingAndRemovingStockPreserveReservedUnits() throws Exception {
        mvc.perform(post("/api/inventory/SKU-A/adjustments").header("Authorization", token("ADMIN"))
                        .contentType("application/json").content("{\"quantityDelta\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStock").value(30))
                .andExpect(jsonPath("$.availableStock").value(27))
                .andExpect(jsonPath("$.reservedStock").value(3));
        mvc.perform(post("/api/inventory/SKU-A/adjustments").header("Authorization", token("ADMIN"))
                        .contentType("application/json").content("{\"quantityDelta\":-27}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStock").value(3))
                .andExpect(jsonPath("$.availableStock").value(0))
                .andExpect(jsonPath("$.reservedStock").value(3));
        assertStock(3, 3, 0);
    }

    @Test
    void removalCannotConsumeReservedStock() throws Exception {
        mvc.perform(post("/api/inventory/SKU-A/adjustments").header("Authorization", token("ADMIN"))
                        .contentType("application/json").content("{\"quantityDelta\":-8}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("available stock")));
        assertStock(10, 3, 7);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, Integer.MAX_VALUE})
    void adjustmentsCannotOverflowStock(int quantity) throws Exception {
        mvc.perform(post("/api/inventory/SKU-A/adjustments").header("Authorization", token("ADMIN"))
                        .contentType("application/json").content("{\"quantityDelta\":" + quantity + "}"))
                .andExpect(status().isConflict());
        assertStock(10, 3, 7);
    }

    @Test
    void maximumIntegerStockIsAllowedAndFurtherIncrementsAreRejected() throws Exception {
        mvc.perform(post("/api/inventory/SKU-A/adjustments").header("Authorization", token("ADMIN"))
                        .contentType("application/json").content("{\"quantityDelta\":2147483637}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalStock").value(Integer.MAX_VALUE));
        mvc.perform(post("/api/inventory/SKU-A/adjustments").header("Authorization", token("ADMIN"))
                        .contentType("application/json").content("{\"quantityDelta\":1}"))
                .andExpect(status().isConflict());
        assertStock(Integer.MAX_VALUE, 3, Integer.MAX_VALUE - 3);
    }

    @Test
    void unknownSkuReturnsNotFoundForReadAndAdjustment() throws Exception {
        mvc.perform(get("/api/inventory/unknown").header("Authorization", token("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/inventory/unknown/adjustments").header("Authorization", token("ADMIN"))
                        .contentType("application/json").content("{\"quantityDelta\":1}"))
                .andExpect(status().isNotFound());
        assertThat(inventories.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"quantityDelta\":null}", "{\"quantityDelta\":0}",
            "{\"quantityDelta\":2147483648}", "{\"quantityDelta\":\"invalid\"}", "{"})
    void invalidAdjustmentIsRejectedWithoutMutation(String body) throws Exception {
        mvc.perform(post("/api/inventory/SKU-A/adjustments").header("Authorization", token("ADMIN"))
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        assertStock(10, 3, 7);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=invalid"})
    void invalidPaginationReturnsBadRequest(String query) throws Exception {
        String[] parameter = query.split("=");
        mvc.perform(get("/api/inventory").header("Authorization", token("ADMIN"))
                        .param(parameter[0], parameter[1]))
                .andExpect(status().isBadRequest());
    }

    @Test
    void everyInventoryEndpointRequiresAnAdminToken() throws Exception {
        for (String path : new String[]{"/api/inventory", "/api/inventory/SKU-A"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", "Bearer invalid"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", token("USER")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/inventory/SKU-A/adjustments").contentType("application/json")
                        .content("{\"quantityDelta\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/inventory/SKU-A/adjustments").header("Authorization", token("USER"))
                        .contentType("application/json").content("{\"quantityDelta\":1}"))
                .andExpect(status().isForbidden());
        assertStock(10, 3, 7);
    }

    private void assertStock(int total, int reserved, int available) {
        Inventory inventory = inventories.findBySku("SKU-A").orElseThrow();
        assertThat(inventory.getTotalStock()).isEqualTo(total);
        assertThat(inventory.getReservedStock()).isEqualTo(reserved);
        assertThat(inventory.getAvailableStock()).isEqualTo(available);
    }

    private String token(String role) {
        return "Bearer " + Jwts.builder().setSubject("inventory-test@example.test")
                .claim("userId", 1L).claim("role", role)
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("test-secret-key-for-unit-testing-purposes-only"
                        .getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
    }
}
