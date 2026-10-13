package com.example.orderinventoryservice.service;

import com.example.orderinventoryservice.dto.*;
import com.example.orderinventoryservice.entity.Order;
import com.example.orderinventoryservice.entity.OrderItem;
import com.example.orderinventoryservice.entity.OrderStatus;
import com.example.orderinventoryservice.entity.OrderStatusHistory;
import com.example.orderinventoryservice.exception.InvalidOrderStatusException;
import com.example.orderinventoryservice.exception.OrderNotFoundException;
import com.example.orderinventoryservice.exception.UnauthorizedOrderAccessException;
import com.example.orderinventoryservice.repository.OrderRepository;
import com.example.orderinventoryservice.event.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final String USER_EMAIL = "user@example.com";

    private CreateOrderRequest createValidRequest() {
        return CreateOrderRequest.builder()
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
    }

    private Order createMockOrder(OrderStatus status) {
        Order order = Order.builder()
                .id(1L)
                .userId(USER_ID)
                .orderCode("ORD-20260930-000001")
                .status(status)
                .totalAmount(new BigDecimal("200000"))
                .shippingAddress("Hanoi, Vietnam")
                .note("Call before delivery")
                .items(new ArrayList<>())
                .statusHistories(new ArrayList<>())
                .build();
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        OrderItem item = OrderItem.builder()
                .id(1L)
                .productId("product-001")
                .sku("IP17-BLK-256")
                .productName("Product IP17-BLK-256")
                .quantity(2)
                .unitPrice(new BigDecimal("100000"))
                .subtotal(new BigDecimal("200000"))
                .build();
        item.setOrder(order);
        order.getItems().add(item);

        OrderStatusHistory history = OrderStatusHistory.builder()
                .id(1L)
                .oldStatus(null)
                .newStatus(OrderStatus.PENDING)
                .changedAt(LocalDateTime.now())
                .changedBy(USER_EMAIL)
                .build();
        history.setOrder(order);
        order.getStatusHistories().add(history);

        return order;
    }

    // ==================== CREATE ORDER ====================

    @Nested
    @DisplayName("Create Order")
    class CreateOrderTests {

        @Test
        @DisplayName("1. Create Order Success")
        void createOrder_success() {
            // Given
            CreateOrderRequest request = createValidRequest();
            when(productServiceClient.getProductInfo(anyString(), anyString()))
                    .thenReturn(new ProductServiceClient.ProductInfo("Product IP17-BLK-256", new BigDecimal("100000")));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                order.setCreatedAt(LocalDateTime.now());
                order.setUpdatedAt(LocalDateTime.now());
                return order;
            });

            // When
            OrderResponse response = orderService.createOrder(request, USER_ID, USER_EMAIL);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("PENDING");
            assertThat(response.getOrderCode()).matches("ORD-\\d{8}-[0-9a-f]{32}");
            verify(orderRepository, never()).existsByOrderCode(anyString());
            assertThat(response.getUserId()).isEqualTo(USER_ID);
            assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("200000"));
            assertThat(response.getItems()).hasSize(1);
            assertThat(response.getStatusHistories()).hasSize(1);
            assertThat(response.getStatusHistories().get(0).getOldStatus()).isNull();
            assertThat(response.getStatusHistories().get(0).getNewStatus()).isEqualTo("PENDING");

            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("16. Create Order creates PENDING history")
        void createOrder_createsHistory() {
            // Given
            CreateOrderRequest request = createValidRequest();
            when(productServiceClient.getProductInfo(anyString(), anyString()))
                    .thenReturn(new ProductServiceClient.ProductInfo("Product IP17-BLK-256", new BigDecimal("100000")));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                order.setCreatedAt(LocalDateTime.now());
                order.setUpdatedAt(LocalDateTime.now());
                return order;
            });

            // When
            OrderResponse response = orderService.createOrder(request, USER_ID, USER_EMAIL);

            // Then
            assertThat(response.getStatusHistories()).hasSize(1);
            OrderStatusHistoryResponse history = response.getStatusHistories().get(0);
            assertThat(history.getOldStatus()).isNull();
            assertThat(history.getNewStatus()).isEqualTo("PENDING");
            assertThat(history.getChangedBy()).isEqualTo(USER_EMAIL);
        }
    }

    // ==================== GET ORDER ====================

    @Nested
    @DisplayName("Get Order")
    class GetOrderTests {

        @Test
        @DisplayName("5. Get Order Success")
        void getOrderById_success() {
            // Given
            Order order = createMockOrder(OrderStatus.PENDING);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // When
            OrderResponse response = orderService.getOrderById(1L, USER_ID);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getOrderCode()).isEqualTo("ORD-20260930-000001");
            assertThat(response.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("6. Order Not Found")
        void getOrderById_notFound() {
            // Given
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> orderService.getOrderById(999L, USER_ID))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("Order not found");
        }

        @Test
        @DisplayName("7. Unauthorized Access")
        void getOrderById_unauthorized() {
            // Given
            Order order = createMockOrder(OrderStatus.PENDING);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // When/Then
            assertThatThrownBy(() -> orderService.getOrderById(1L, OTHER_USER_ID))
                    .isInstanceOf(UnauthorizedOrderAccessException.class)
                    .hasMessageContaining("not authorized");
        }
    }

    // ==================== GET MY ORDERS ====================

    @Nested
    @DisplayName("Get My Orders")
    class GetMyOrdersTests {

        @Test
        @DisplayName("8. Get Current User Orders")
        void getMyOrders_success() {
            // Given
            Order order = createMockOrder(OrderStatus.PENDING);
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(order));

            // When
            List<OrderResponse> orders = orderService.getMyOrders(USER_ID);

            // Then
            assertThat(orders).hasSize(1);
            assertThat(orders.get(0).getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("9. User has no orders")
        void getMyOrders_empty() {
            // Given
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Collections.emptyList());

            // When
            List<OrderResponse> orders = orderService.getMyOrders(USER_ID);

            // Then
            assertThat(orders).isEmpty();
        }
    }

    // ==================== CANCEL ORDER ====================

    @Nested
    @DisplayName("Cancel Order")
    class CancelOrderTests {

        @Test
        @DisplayName("10. Cancel PENDING Success")
        void cancelOrder_pendingSuccess() {
            // Given
            Order order = createMockOrder(OrderStatus.PENDING);
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OrderResponse response = orderService.cancelOrder(1L, USER_ID, USER_EMAIL);

            // Then
            assertThat(response.getStatus()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("11. Cancel CONFIRMED is rejected without changing stock or publishing")
        void cancelOrder_confirmedRejected() {
            Order order = createMockOrder(OrderStatus.CONFIRMED);
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L, USER_ID, USER_EMAIL))
                    .isInstanceOf(InvalidOrderStatusException.class);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(orderRepository, never()).save(any());
            verifyNoInteractions(inventoryService, eventPublisher);
        }

        @Test
        @DisplayName("12. Cancel SHIPPING Fail")
        void cancelOrder_shippingFail() {
            // Given
            Order order = createMockOrder(OrderStatus.SHIPPING);
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

            // When/Then
            assertThatThrownBy(() -> orderService.cancelOrder(1L, USER_ID, USER_EMAIL))
                    .isInstanceOf(InvalidOrderStatusException.class)
                    .hasMessageContaining("SHIPPING");
        }

        @Test
        @DisplayName("13. Cancel COMPLETED Fail")
        void cancelOrder_completedFail() {
            // Given
            Order order = createMockOrder(OrderStatus.COMPLETED);
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

            // When/Then
            assertThatThrownBy(() -> orderService.cancelOrder(1L, USER_ID, USER_EMAIL))
                    .isInstanceOf(InvalidOrderStatusException.class)
                    .hasMessageContaining("COMPLETED");
        }

        @Test
        @DisplayName("14. Cancel CANCELLED Fail")
        void cancelOrder_cancelledFail() {
            // Given
            Order order = createMockOrder(OrderStatus.CANCELLED);
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

            // When/Then
            assertThatThrownBy(() -> orderService.cancelOrder(1L, USER_ID, USER_EMAIL))
                    .isInstanceOf(InvalidOrderStatusException.class)
                    .hasMessageContaining("CANCELLED");
        }

        @Test
        @DisplayName("15. Cancel order of another user Fail")
        void cancelOrder_otherUserFail() {
            // Given
            Order order = createMockOrder(OrderStatus.PENDING);
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

            // When/Then
            assertThatThrownBy(() -> orderService.cancelOrder(1L, OTHER_USER_ID, "other@example.com"))
                    .isInstanceOf(UnauthorizedOrderAccessException.class)
                    .hasMessageContaining("not authorized");
        }

        @Test
        @DisplayName("17. Cancel creates CANCELLED history")
        void cancelOrder_createsHistory() {
            // Given
            Order order = createMockOrder(OrderStatus.PENDING);
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            OrderResponse response = orderService.cancelOrder(1L, USER_ID, USER_EMAIL);

            // Then
            assertThat(response.getStatusHistories()).hasSizeGreaterThanOrEqualTo(2);
            OrderStatusHistoryResponse lastHistory = response.getStatusHistories()
                    .get(response.getStatusHistories().size() - 1);
            assertThat(lastHistory.getOldStatus()).isEqualTo("PENDING");
            assertThat(lastHistory.getNewStatus()).isEqualTo("CANCELLED");
        }
    }

    @Nested
    class PaymentSagaTests {
        @Test
        void paymentSuccessConfirmsOnce() {
            Order order = createMockOrder(OrderStatus.PENDING);
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

            orderService.confirmPayment(1L);
            orderService.confirmPayment(1L);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getStatusHistories()).hasSize(2);
            verify(inventoryService).confirmStock(1L);
            verify(orderRepository).save(order);
        }

        @Test
        void paymentFailureReleasesAndCancelsOnce() {
            Order order = createMockOrder(OrderStatus.PENDING);
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

            orderService.failPayment(1L);
            orderService.failPayment(1L);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getStatusHistories()).hasSize(2);
            verify(inventoryService).releaseStock(1L);
            verify(eventPublisher).publishOrderCancelledEvent(any());
        }
    }
}
