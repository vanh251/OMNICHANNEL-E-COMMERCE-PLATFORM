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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.example.orderinventoryservice.event.EventPublisher;
import com.example.orderinventoryservice.event.OrderCreatedEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductServiceClient productServiceClient;
    private final InventoryService inventoryService;
    private final EventPublisher eventPublisher;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Valid status transitions for the order state machine.
     */
    private static final Set<String> VALID_TRANSITIONS = Set.of(
            "PENDING->CONFIRMED",
            "CONFIRMED->SHIPPING",
            "SHIPPING->COMPLETED",
            "PENDING->CANCELLED",
            "CONFIRMED->CANCELLED"
    );

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, Long userId, String userEmail) {
        String orderCode = generateOrderCode();

        Order order = Order.builder()
                .userId(userId)
                .orderCode(orderCode)
                .status(OrderStatus.PENDING)
                .shippingAddress(request.getShippingAddress())
                .note(request.getNote())
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {
            // Fetch product info from Product Service (stub for Day 5)
            ProductServiceClient.ProductInfo productInfo =
                    productServiceClient.getProductInfo(itemRequest.getProductId(), itemRequest.getSku());

            if (productInfo == null) {
                log.error("Failed to retrieve product info via gRPC for sku={}", itemRequest.getSku());
                throw new IllegalArgumentException("Product not found, inactive, or service unavailable for SKU: " + itemRequest.getSku());
            }

            BigDecimal unitPrice = productInfo.unitPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            OrderItem item = OrderItem.builder()
                    .productId(itemRequest.getProductId())
                    .sku(itemRequest.getSku())
                    .productName(productInfo.productName())
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .build();

            order.addItem(item);
            totalAmount = totalAmount.add(subtotal);
        }

        order.setTotalAmount(totalAmount);

        // Create initial status history: null -> PENDING
        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .oldStatus(null)
                .newStatus(OrderStatus.PENDING)
                .changedAt(LocalDateTime.now())
                .changedBy(userEmail)
                .build();
        order.addStatusHistory(statusHistory);

        Order savedOrder = orderRepository.save(order);
        
        // Reserve stock
        for (OrderItem item : savedOrder.getItems()) {
            inventoryService.reserveStock(savedOrder.getId(), item.getSku(), item.getQuantity());
        }

        // Publish OrderCreatedEvent
        OrderCreatedEvent.OrderCreatedEventData eventData = OrderCreatedEvent.OrderCreatedEventData.builder()
                .orderId(savedOrder.getId().toString())
                .customerId(userId.toString())
                .totalAmount(savedOrder.getTotalAmount())
                .currency("VND")
                .items(savedOrder.getItems().stream()
                        .map(item -> OrderCreatedEvent.OrderItemData.builder()
                                .productId(item.getProductId())
                                .sku(item.getSku())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .build())
                        .collect(Collectors.toList()))
                .build();

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("OrderCreatedEvent")
                .timestamp(LocalDateTime.now().toString())
                .data(eventData)
                .build();

        eventPublisher.publishOrderCreatedEvent(event);

        log.info("Created order with id={}, orderCode={}, userId={}", savedOrder.getId(), orderCode, userId);

        return mapToResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        if (!order.getUserId().equals(userId)) {
            throw new UnauthorizedOrderAccessException("You are not authorized to access this order");
        }

        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, Long userId, String userEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        if (!order.getUserId().equals(userId)) {
            throw new UnauthorizedOrderAccessException("You are not authorized to cancel this order");
        }

        OrderStatus currentStatus = order.getStatus();
        validateStatusTransition(currentStatus, OrderStatus.CANCELLED);

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .oldStatus(currentStatus)
                .newStatus(OrderStatus.CANCELLED)
                .changedAt(LocalDateTime.now())
                .changedBy(userEmail)
                .build();
        order.addStatusHistory(statusHistory);

        order.setStatus(OrderStatus.CANCELLED);

        // Release stock if order is pending
        inventoryService.releaseStock(orderId);

        Order savedOrder = orderRepository.save(order);
        eventPublisher.publishOrderCancelledEvent(cancellationEvent(savedOrder, "CUSTOMER_CANCELLED"));
        log.info("Cancelled order id={}, previousStatus={}", orderId, currentStatus);

        return mapToResponse(savedOrder);
    }

    @Transactional
    public void confirmPayment(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        if (order.getStatus() == OrderStatus.CONFIRMED) return;
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Ignoring payment success for orderId={} in status={}", orderId, order.getStatus());
            return;
        }
        inventoryService.confirmStock(orderId);
        changeStatus(order, OrderStatus.CONFIRMED, "PAYMENT");
        orderRepository.save(order);
    }

    @Transactional
    public void failPayment(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) return;
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Ignoring payment failure for orderId={} in status={}", orderId, order.getStatus());
            return;
        }
        inventoryService.releaseStock(orderId);
        changeStatus(order, OrderStatus.CANCELLED, "PAYMENT");
        orderRepository.save(order);
        eventPublisher.publishOrderCancelledEvent(cancellationEvent(order, "PAYMENT_FAILED"));
    }

    private void changeStatus(Order order, OrderStatus status, String actor) {
        order.addStatusHistory(OrderStatusHistory.builder()
                .oldStatus(order.getStatus()).newStatus(status)
                .changedAt(LocalDateTime.now()).changedBy(actor).build());
        order.setStatus(status);
    }

    private java.util.Map<String, Object> cancellationEvent(Order order, String reason) {
        return java.util.Map.of("eventId", UUID.randomUUID().toString(),
                "eventType", "OrderCancelledEvent", "timestamp", java.time.Instant.now().toString(),
                "data", java.util.Map.of("orderId", order.getId().toString(),
                        "customerId", order.getUserId().toString(), "reason", reason,
                        "cancelledAt", java.time.Instant.now().toString()));
    }

    // ==================== Helper Methods ====================

    private void validateStatusTransition(OrderStatus from, OrderStatus to) {
        String transition = from.name() + "->" + to.name();
        if (!VALID_TRANSITIONS.contains(transition)) {
            throw new InvalidOrderStatusException(
                    "Order cannot be cancelled in current status: " + from.name());
        }
    }

    private String generateOrderCode() {
        String dateStr = LocalDate.now().format(DATE_FORMATTER);
        String uniquePart = String.format("%06d", System.nanoTime() % 1_000_000);
        String orderCode = "ORD-" + dateStr + "-" + uniquePart;

        // Ensure uniqueness
        int attempt = 0;
        while (orderRepository.existsByOrderCode(orderCode) && attempt < 10) {
            uniquePart = String.format("%06d", System.nanoTime() % 1_000_000);
            orderCode = "ORD-" + dateStr + "-" + uniquePart;
            attempt++;
        }

        return orderCode;
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(this::mapToItemResponse)
                .collect(Collectors.toList());

        List<OrderStatusHistoryResponse> historyResponses = order.getStatusHistories().stream()
                .map(this::mapToHistoryResponse)
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .userId(order.getUserId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .shippingAddress(order.getShippingAddress())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(itemResponses)
                .statusHistories(historyResponses)
                .build();
    }

    private OrderItemResponse mapToItemResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .sku(item.getSku())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }

    private OrderStatusHistoryResponse mapToHistoryResponse(OrderStatusHistory history) {
        return OrderStatusHistoryResponse.builder()
                .id(history.getId())
                .oldStatus(history.getOldStatus() != null ? history.getOldStatus().name() : null)
                .newStatus(history.getNewStatus().name())
                .changedAt(history.getChangedAt())
                .changedBy(history.getChangedBy())
                .build();
    }
}
