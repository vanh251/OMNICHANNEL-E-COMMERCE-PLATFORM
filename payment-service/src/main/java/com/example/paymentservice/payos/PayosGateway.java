package com.example.paymentservice.payos;

import java.util.Map;
import java.util.Optional;
import vn.payos.model.webhooks.WebhookData;

public interface PayosGateway {
    record Link(Long orderCode, Long amount, Long amountPaid, String id, String status,
                String checkoutUrl, String qrCode, String reference) {}

    Optional<Link> find(long orderCode);
    Link create(long orderCode, long amount, long expiresAt);
    Link cancel(long orderCode);
    WebhookData verify(Map<String, Object> webhook);
}
