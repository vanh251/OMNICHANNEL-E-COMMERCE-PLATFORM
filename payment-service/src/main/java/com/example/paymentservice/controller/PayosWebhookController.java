package com.example.paymentservice.controller;

import com.example.paymentservice.payos.PayosGateway;
import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.provider", havingValue = "payos")
public class PayosWebhookController {
    private final PayosGateway gateway;
    private final PaymentService payments;

    @PostMapping("/api/payments/webhooks/payos")
    public ResponseEntity<Map<String, Object>> receive(@RequestBody Map<String, Object> body) {
        vn.payos.model.webhooks.WebhookData verified;
        try {
            verified = gateway.verify(body);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid payOS signature or payload"));
        }
        payments.applyPayosWebhook(verified);
        return ResponseEntity.ok(Map.of("received", true));
    }

    @GetMapping({"/api/payments/payos/return", "/api/payments/payos/cancel"})
    public Map<String, String> returnFromCheckout() {
        // Browser query parameters cannot prove payment or cancel an order.
        return Map.of("message", "Vui lòng kiểm tra trạng thái đơn hàng trong ứng dụng.",
                "paymentStatus", "VERIFY_IN_APP");
    }
}
