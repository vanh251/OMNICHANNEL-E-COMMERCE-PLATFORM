package com.example.orderinventoryservice.controller;

import com.example.orderinventoryservice.dto.CreateOrderRequest;
import com.example.orderinventoryservice.dto.AdminOrderSummaryResponse;
import com.example.orderinventoryservice.dto.ErrorResponse;
import com.example.orderinventoryservice.dto.OrderResponse;
import com.example.orderinventoryservice.security.JwtUserPrincipal;
import com.example.orderinventoryservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order Controller", description = "APIs for order management")
@SecurityRequirement(name = "BearerAuth")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Create a new order", description = "Creates a new order for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created successfully",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - JWT missing or invalid")
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication) {
        JwtUserPrincipal principal = (JwtUserPrincipal) authentication.getPrincipal();
        OrderResponse response = orderService.createOrder(request, principal.getUserId(), principal.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID", description = "Returns order details for the given order ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - JWT missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Forbidden - order belongs to another user",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable("id") Long id,
            Authentication authentication) {
        JwtUserPrincipal principal = (JwtUserPrincipal) authentication.getPrincipal();
        OrderResponse response = orderService.getOrderById(id, principal.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-orders")
    @Operation(summary = "Get my orders", description = "Returns all orders for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - JWT missing or invalid")
    })
    public ResponseEntity<List<OrderResponse>> getMyOrders(Authentication authentication) {
        JwtUserPrincipal principal = (JwtUserPrincipal) authentication.getPrincipal();
        List<OrderResponse> orders = orderService.getMyOrders(principal.getUserId());
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List orders for administrators", description = "Optionally filter by status; newest orders first")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Page of order summaries"),
            @ApiResponse(responseCode = "400", description = "Invalid status or pagination"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Administrator role required")})
    public ResponseEntity<Page<AdminOrderSummaryResponse>> getAdminOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.getAdminOrders(status, page, size));
    }

    @GetMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get order details for administrators")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Order details"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Administrator role required"),
            @ApiResponse(responseCode = "404", description = "Order not found")})
    public ResponseEntity<OrderResponse> getAdminOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getAdminOrderById(id));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Start shipping", description = "Moves a CONFIRMED order to SHIPPING")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Order is shipping"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Administrator role required"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order is not CONFIRMED")})
    public ResponseEntity<OrderResponse> shipOrder(@PathVariable Long id, Authentication authentication) {
        JwtUserPrincipal principal = (JwtUserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(orderService.shipOrder(id, principal.getEmail()));
    }

    @PostMapping("/{id}/confirm-receipt")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Confirm receipt", description = "The order owner moves a SHIPPING order to COMPLETED")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Receipt confirmed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Customer role and order ownership required"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order is not SHIPPING")})
    public ResponseEntity<OrderResponse> confirmReceipt(@PathVariable Long id, Authentication authentication) {
        JwtUserPrincipal principal = (JwtUserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(orderService.confirmReceipt(id, principal.getUserId(), principal.getEmail()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel order", description = "Cancels an order only while it is PENDING")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled successfully",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "409", description = "Order cannot be cancelled in current status",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - JWT missing or invalid"),
            @ApiResponse(responseCode = "403", description = "Forbidden - order belongs to another user",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable("id") Long id,
            Authentication authentication) {
        JwtUserPrincipal principal = (JwtUserPrincipal) authentication.getPrincipal();
        OrderResponse response = orderService.cancelOrder(id, principal.getUserId(), principal.getEmail());
        return ResponseEntity.ok(response);
    }
}
