package com.example.orderinventoryservice.service;

import com.example.orderinventoryservice.entity.Inventory;
import com.example.orderinventoryservice.exception.InsufficientStockException;
import com.example.orderinventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
public class InventoryConcurrencyIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @MockitoBean
    private ProductServiceClient productServiceClient; // To satisfy app context

    private static final String FLASH_SKU = "FLASH-SKU-001";

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();

        // Seed 1 item in stock
        Inventory inventory = Inventory.builder()
                .productId("flash-prod-1")
                .sku(FLASH_SKU)
                .totalStock(1)
                .reservedStock(0)
                .availableStock(1)
                .build();
        inventoryRepository.save(inventory);
    }

    @AfterEach
    void tearDown() {
        inventoryRepository.deleteAll();
    }

    @Test
    void testFlashSaleConcurrency_onlyOneSuccess() throws InterruptedException {
        int numberOfThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final long orderId = 1000L + i;
            executorService.execute(() -> {
                try {
                    // All threads try to reserve 1 item simultaneously
                    inventoryService.reserveStock(orderId, FLASH_SKU, 1);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    // Other exceptions if any
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Assertions
        assertEquals(1, successCount.get(), "Only one request should succeed in a flash sale of 1 item");
        assertEquals(99, failureCount.get(), "Exactly 99 requests should fail");

        Inventory inventory = inventoryRepository.findBySku(FLASH_SKU).orElseThrow();
        assertEquals(1, inventory.getTotalStock());
        assertEquals(1, inventory.getReservedStock());
        assertEquals(0, inventory.getAvailableStock());
    }
}
