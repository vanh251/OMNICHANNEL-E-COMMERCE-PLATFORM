package com.example.paymentservice.controller;

import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.paymentservice.security.JwtUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Controller", description = "APIs for payment processing")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get payment by Order ID")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(@PathVariable("orderId") String orderId,
                                                             @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ResponseEntity.ok(paymentService.getPaymentByOrderId(orderId, principal));
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Refund a payment")
    public ResponseEntity<PaymentResponse> refundPayment(@PathVariable("id") Long id) {
        return ResponseEntity.ok(paymentService.refundPayment(id));
    }
}
