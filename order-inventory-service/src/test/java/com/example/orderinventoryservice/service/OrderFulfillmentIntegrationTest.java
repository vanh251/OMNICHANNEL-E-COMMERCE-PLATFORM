package com.example.orderinventoryservice.service;

import com.example.orderinventoryservice.entity.Order;
import com.example.orderinventoryservice.entity.OrderStatus;
import com.example.orderinventoryservice.exception.InvalidOrderStatusException;
import com.example.orderinventoryservice.repository.OrderRepository;
import com.example.orderinventoryservice.repository.OrderStatusHistoryRepository;
import com.example.orderinventoryservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OrderFulfillmentIntegrationTest {
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderStatusHistoryRepository historyRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @MockitoBean private ProductServiceClient productServiceClient;

    @BeforeEach
    void clean() {
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void concurrentShippingWritesOneHistoryAndOutboxEvent() throws Exception {
        Order order = orderRepository.save(Order.builder()
                .userId(1L)
                .orderCode("FULFILLMENT-CONCURRENT-1")
                .status(OrderStatus.CONFIRMED)
                .totalAmount(BigDecimal.TEN)
                .build());
        long orderId = order.getId();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> ship(start, orderId));
            var second = executor.submit(() -> ship(start, orderId));
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        Order saved = orderRepository.findById(orderId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(historyRepository.findByOrderIdOrderByChangedAtAsc(orderId)).hasSize(1);
        assertThat(outboxEventRepository.findAll()).hasSize(1)
                .allMatch(event -> event.getRoutingKey().equals("order.shipped"));

        orderService.confirmReceipt(orderId, 1L, "customer@example.com");
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(historyRepository.findByOrderIdOrderByChangedAtAsc(orderId)).hasSize(2);
        assertThat(outboxEventRepository.findAll()).extracting(event -> event.getRoutingKey())
                .containsExactlyInAnyOrder("order.shipped", "order.completed");
    }

    private int ship(CountDownLatch start, long orderId) throws InterruptedException {
        start.await();
        try {
            orderService.shipOrder(orderId, "admin@example.com");
            return 1;
        } catch (InvalidOrderStatusException exception) {
            return 0;
        }
    }
}
