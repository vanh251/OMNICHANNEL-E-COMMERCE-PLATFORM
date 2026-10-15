package com.example.orderinventoryservice.service;

import com.example.orderinventoryservice.entity.Inventory;
import com.example.orderinventoryservice.PostgresTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.example.orderinventoryservice.exception.InsufficientStockException;
import com.example.orderinventoryservice.exception.InvalidStockAdjustmentException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.example.orderinventoryservice.repository.InventoryRepository;
import com.example.orderinventoryservice.repository.InventoryReservationRepository;
import com.example.orderinventoryservice.repository.OrderRepository;
import com.example.orderinventoryservice.repository.OutboxEventRepository;
import com.example.orderinventoryservice.dto.CreateOrderRequest;
import com.example.orderinventoryservice.dto.OrderItemRequest;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.List;
import java.util.ArrayList;
import java.math.BigDecimal;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
@ActiveProfiles("test")
public class InventoryConcurrencyIntegrationTest {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.configure(registry);
    }

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @MockitoBean
    private ProductServiceClient productServiceClient; // To satisfy app context

    private static final String FLASH_SKU = "FLASH-SKU-001";

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        reservationRepository.deleteAll();
        orderRepository.deleteAll();
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
        outboxRepository.deleteAll();
        reservationRepository.deleteAll();
        orderRepository.deleteAll();
        inventoryRepository.deleteAll();
    }

    @Test
    void testFlashSaleConcurrency_onlyOneSuccess() throws InterruptedException {
        int numberOfThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger unexpectedFailures = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < numberOfThreads; i++) {
            final long orderId = 1000L + i;
            executorService.execute(() -> {
                try {
                    start.await();
                    // All threads try to reserve 1 item simultaneously
                    inventoryService.reserveStock(orderId, FLASH_SKU, 1);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    unexpectedFailures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        start.countDown();
        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executorService.shutdownNow();
        }

        // Assertions
        assertEquals(1, successCount.get(), "Only one request should succeed in a flash sale of 1 item");
        assertEquals(99, failureCount.get(), "Exactly 99 requests should fail");
        assertEquals(0, unexpectedFailures.get(), "No lock timeout or unexpected exception is acceptable");

        Inventory inventory = inventoryRepository.findBySku(FLASH_SKU).orElseThrow();
        assertEquals(1, inventory.getTotalStock());
        assertEquals(1, inventory.getReservedStock());
        assertEquals(0, inventory.getAvailableStock());
    }

    @Test
    void multipleReservationsForSameSkuInOneTransactionAccumulate() {
        Inventory inventory = inventoryRepository.findBySku(FLASH_SKU).orElseThrow();
        inventory.setTotalStock(10);
        inventory.setAvailableStock(10);
        inventoryRepository.save(inventory);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            inventoryService.reserveStock(1L, FLASH_SKU, 2);
            inventoryService.reserveStock(1L, FLASH_SKU, 3);
        });
        Inventory result = inventoryRepository.findBySku(FLASH_SKU).orElseThrow();
        assertThat(result.getReservedStock()).isEqualTo(5);
        assertThat(result.getAvailableStock()).isEqualTo(5);
        inventoryService.confirmStock(1L);
        inventoryService.confirmStock(1L);
        result = inventoryRepository.findBySku(FLASH_SKU).orElseThrow();
        assertThat(result.getTotalStock()).isEqualTo(5);
        assertThat(result.getReservedStock()).isZero();
    }

    @Test
    void laterStockFailureRollsBackEarlierReservation() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            inventoryService.reserveStock(1L, FLASH_SKU, 1);
            inventoryService.reserveStock(1L, "MISSING-SKU", 1);
        })).isInstanceOf(InsufficientStockException.class);
        assertThat(inventoryRepository.findBySku(FLASH_SKU).orElseThrow().getAvailableStock()).isEqualTo(1);
        assertThat(reservationRepository.findByOrderId(1L)).isEmpty();
    }

    @Test
    void nonPositiveQuantityCannotIncreaseAvailableStock() {
        assertThatThrownBy(() -> inventoryService.reserveStock(1L, FLASH_SKU, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inventoryService.reserveStock(1L, FLASH_SKU, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(inventoryRepository.findBySku(FLASH_SKU).orElseThrow().getAvailableStock()).isEqualTo(1);
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void concurrentOrdersWithOppositeItemOrderDoNotDeadlockOrDuplicateOrderCodes() throws Exception {
        Inventory first = inventoryRepository.findBySku(FLASH_SKU).orElseThrow();
        first.setTotalStock(20);
        first.setAvailableStock(20);
        inventoryRepository.save(first);
        String otherSku = "SECOND-SKU";
        inventoryRepository.save(Inventory.builder().productId("flash-prod-1").sku(otherSku)
                .totalStock(20).reservedStock(0).availableStock(20).build());
        when(productServiceClient.getProductInfo(anyString(), anyString()))
                .thenReturn(new ProductServiceClient.ProductInfo("Product", BigDecimal.ONE));
        var firstItem = OrderItemRequest.builder().productId("flash-prod-1").sku(FLASH_SKU).quantity(1).build();
        var secondItem = OrderItemRequest.builder().productId("flash-prod-1").sku(otherSku).quantity(1).build();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 20; i++) {
                var items = i % 2 == 0 ? List.of(firstItem, secondItem) : List.of(secondItem, firstItem);
                futures.add(executor.submit(() -> orderService.createOrder(CreateOrderRequest.builder()
                        .items(items).shippingAddress("Test").build(), 1L, "test@example.test").getOrderCode()));
            }
            List<String> codes = new ArrayList<>();
            for (var future : futures) {
                codes.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(codes).hasSize(20).doesNotHaveDuplicates();
            assertThat(orderRepository.count()).isEqualTo(20);
            assertThat(outboxRepository.count()).isEqualTo(40);
            assertThat(reservationRepository.count()).isEqualTo(40);
            assertThat(inventoryRepository.findAll()).allSatisfy(stock -> {
                assertThat(stock.getAvailableStock()).isZero();
                assertThat(stock.getReservedStock()).isEqualTo(20);
                assertThat(stock.getTotalStock()).isEqualTo(20);
            });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void insufficientStockRollsBackOrderOutboxAndEarlierSku() {
        when(productServiceClient.getProductInfo(anyString(), anyString()))
                .thenReturn(new ProductServiceClient.ProductInfo("Product", BigDecimal.ONE));
        var items = List.of(
                OrderItemRequest.builder().productId("product").sku(FLASH_SKU).quantity(1).build(),
                OrderItemRequest.builder().productId("product").sku("MISSING-SKU").quantity(1).build());
        assertThatThrownBy(() -> orderService.createOrder(CreateOrderRequest.builder()
                .items(items).shippingAddress("Test").build(), 1L, "test@example.test"))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(orderRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
        assertThat(reservationRepository.count()).isZero();
        assertThat(inventoryRepository.findBySku(FLASH_SKU).orElseThrow().getAvailableStock()).isEqualTo(1);
    }

    @Test
    void releaseReturnsStockOnlyOnce() {
        inventoryService.reserveStock(1L, FLASH_SKU, 1);
        inventoryService.releaseStock(1L);
        inventoryService.releaseStock(1L);
        Inventory result = inventoryRepository.findBySku(FLASH_SKU).orElseThrow();
        assertThat(result.getTotalStock()).isEqualTo(1);
        assertThat(result.getReservedStock()).isZero();
        assertThat(result.getAvailableStock()).isEqualTo(1);
    }

    @Test
    void inconsistentStockRollsBackConfirmationStatus() {
        inventoryService.reserveStock(1L, FLASH_SKU, 1);
        Inventory stock = inventoryRepository.findBySku(FLASH_SKU).orElseThrow();
        stock.setReservedStock(0);
        stock.setAvailableStock(1);
        inventoryRepository.save(stock);
        assertThatThrownBy(() -> inventoryService.confirmStock(1L)).isInstanceOf(IllegalStateException.class);
        assertThat(reservationRepository.findByOrderId(1L)).allSatisfy(reservation ->
                assertThat(reservation.getStatus()).isEqualTo(
                        com.example.orderinventoryservice.entity.InventoryReservation.ReservationStatus.PENDING));
    }

    @Test
    void racingPaymentSuccessAndFailureProduceExactlyOneTerminalTransition() throws Exception {
        when(productServiceClient.getProductInfo(anyString(), anyString()))
                .thenReturn(new ProductServiceClient.ProductInfo("Product", BigDecimal.ONE));
        Long orderId = orderService.createOrder(CreateOrderRequest.builder().items(List.of(
                OrderItemRequest.builder().productId("product").sku(FLASH_SKU).quantity(1).build()))
                .shippingAddress("Test").build(), 1L, "test@example.test").getId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> success = executor.submit(() -> {
                awaitStart(start);
                orderService.confirmPayment(orderId);
            });
            Future<?> failure = executor.submit(() -> {
                awaitStart(start);
                orderService.failPayment(orderId);
            });
            start.countDown();
            success.get(30, TimeUnit.SECONDS);
            failure.get(30, TimeUnit.SECONDS);
            var order = orderService.getOrderById(orderId, 1L);
            assertThat(order.getStatus()).isIn("CONFIRMED", "CANCELLED");
            assertThat(order.getStatusHistories()).hasSize(2);
            Inventory result = inventoryRepository.findBySku(FLASH_SKU).orElseThrow();
            assertThat(result.getReservedStock()).isZero();
            int expectedStock = order.getStatus().equals("CONFIRMED") ? 0 : 1;
            assertThat(result.getTotalStock()).isEqualTo(expectedStock);
            assertThat(result.getAvailableStock()).isEqualTo(expectedStock);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void racingCustomerCancellationAndPaymentKeepStockConsistent() throws Exception {
        when(productServiceClient.getProductInfo(anyString(), anyString()))
                .thenReturn(new ProductServiceClient.ProductInfo("Product", BigDecimal.ONE));
        Long orderId = orderService.createOrder(CreateOrderRequest.builder().items(List.of(
                OrderItemRequest.builder().productId("product").sku(FLASH_SKU).quantity(1).build()))
                .shippingAddress("Test").build(), 1L, "test@example.test").getId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> payment = executor.submit(() -> {
                awaitStart(start);
                orderService.confirmPayment(orderId);
            });
            Future<Boolean> cancel = executor.submit(() -> {
                awaitStart(start);
                try {
                    orderService.cancelOrder(orderId, 1L, "test@example.test");
                    return true;
                } catch (com.example.orderinventoryservice.exception.InvalidOrderStatusException ex) {
                    return false;
                }
            });
            start.countDown();
            payment.get(30, TimeUnit.SECONDS);
            boolean cancelled = cancel.get(30, TimeUnit.SECONDS);
            var order = orderService.getOrderById(orderId, 1L);
            assertThat(order.getStatus()).isEqualTo(cancelled ? "CANCELLED" : "CONFIRMED");
            assertThat(order.getStatusHistories()).hasSize(2);
            Inventory stock = inventoryRepository.findBySku(FLASH_SKU).orElseThrow();
            assertThat(stock.getReservedStock()).isZero();
            assertThat(stock.getTotalStock()).isEqualTo(cancelled ? 1 : 0);
            assertThat(stock.getAvailableStock()).isEqualTo(cancelled ? 1 : 0);
            assertThat(outboxRepository.findAll().stream()
                    .filter(event -> event.getRoutingKey().equals("order.cancelled")).count())
                    .isEqualTo(cancelled ? 1 : 0);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentRestocksAndReservationsDoNotLoseUpdates() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger reserved = new AtomicInteger();
        try {
            for (int i = 0; i < 40; i++) {
                final long orderId = 5000L + i;
                futures.add(executor.submit(() -> {
                    awaitStart(start);
                    inventoryService.adjustStock(FLASH_SKU, 1, "admin@example.test");
                }));
                futures.add(executor.submit(() -> {
                    awaitStart(start);
                    try {
                        inventoryService.reserveStock(orderId, FLASH_SKU, 1);
                        reserved.incrementAndGet();
                    } catch (InsufficientStockException expected) {
                        // Reservations may precede restocks; they must never oversell.
                    }
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            Inventory result = inventoryRepository.findBySku(FLASH_SKU).orElseThrow();
            assertThat(result.getTotalStock()).isEqualTo(41);
            assertThat(result.getReservedStock()).isEqualTo(reserved.get());
            assertThat(result.getAvailableStock()).isEqualTo(41 - reserved.get());
            assertThat(reservationRepository.count()).isEqualTo(reserved.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void removalAndReservationCannotBothConsumeTheLastUnit() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> adjustment = executor.submit(() -> {
                awaitStart(start);
                try {
                    inventoryService.adjustStock(FLASH_SKU, -1, "admin@example.test");
                    return true;
                } catch (InvalidStockAdjustmentException expected) {
                    return false;
                }
            });
            Future<Boolean> reservation = executor.submit(() -> {
                awaitStart(start);
                try {
                    inventoryService.reserveStock(6000L, FLASH_SKU, 1);
                    return true;
                } catch (InsufficientStockException expected) {
                    return false;
                }
            });
            start.countDown();
            boolean removed = adjustment.get(30, TimeUnit.SECONDS);
            boolean reserved = reservation.get(30, TimeUnit.SECONDS);
            assertThat(removed).isNotEqualTo(reserved);
            Inventory result = inventoryRepository.findBySku(FLASH_SKU).orElseThrow();
            assertThat(result.getAvailableStock()).isZero();
            assertThat(result.getTotalStock()).isEqualTo(reserved ? 1 : 0);
            assertThat(result.getReservedStock()).isEqualTo(reserved ? 1 : 0);
            assertThat(reservationRepository.count()).isEqualTo(reserved ? 1 : 0);
        } finally {
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void restockRacingConfirmationOrReleasePreservesStock(boolean confirm) throws Exception {
        inventoryService.reserveStock(7000L, FLASH_SKU, 1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> restock = executor.submit(() -> {
                awaitStart(start);
                inventoryService.adjustStock(FLASH_SKU, 20, "admin@example.test");
            });
            Future<?> terminal = executor.submit(() -> {
                awaitStart(start);
                if (confirm) {
                    inventoryService.confirmStock(7000L);
                } else {
                    inventoryService.releaseStock(7000L);
                }
            });
            start.countDown();
            restock.get(30, TimeUnit.SECONDS);
            terminal.get(30, TimeUnit.SECONDS);
            Inventory result = inventoryRepository.findBySku(FLASH_SKU).orElseThrow();
            assertThat(result.getReservedStock()).isZero();
            assertThat(result.getTotalStock()).isEqualTo(confirm ? 20 : 21);
            assertThat(result.getAvailableStock()).isEqualTo(result.getTotalStock());
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitStart(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
