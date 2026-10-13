package com.example.paymentservice.payos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import vn.payos.PayOS;
import vn.payos.exception.APIException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.webhooks.WebhookData;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "payos")
public class SdkPayosGateway implements PayosGateway {
    private final PayOS client;
    private final String returnUrl;
    private final String cancelUrl;

    public SdkPayosGateway(PayOS client, @Value("${payos.return-url}") String returnUrl,
                           @Value("${payos.cancel-url}") String cancelUrl) {
        this.client = client;
        this.returnUrl = validUrl(returnUrl);
        this.cancelUrl = validUrl(cancelUrl);
    }

    private String validUrl(String value) {
        URI uri = URI.create(value);
        Assert.isTrue(uri.getHost() != null && ("https".equals(uri.getScheme())
                || ("http".equals(uri.getScheme()) && ("localhost".equals(uri.getHost())
                || "127.0.0.1".equals(uri.getHost())))), "payOS redirect requires HTTPS or loopback HTTP");
        return value;
    }

    @Override
    public Optional<Link> find(long orderCode) {
        try {
            return Optional.of(convert(client.paymentRequests().get(orderCode)));
        } catch (APIException ex) {
            // A provider-level 'not found' is different from an outage or invalid credentials.
            if (ex.getStatusCode().orElse(0) == 404
                    || java.util.Set.of("101", "231").contains(ex.getErrorCode().orElse(""))) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    @Override
    public Link create(long orderCode, long amount, long expiresAt) {
        var response = client.paymentRequests().create(CreatePaymentLinkRequest.builder()
                .orderCode(orderCode).amount(amount).description("DH" + (orderCode % 10000000))
                .returnUrl(returnUrl).cancelUrl(cancelUrl).expiredAt(expiresAt).build());
        if (!"VND".equals(response.getCurrency())) throw new IllegalArgumentException("payOS response currency mismatch");
        return new Link(response.getOrderCode(), response.getAmount(), 0L, response.getPaymentLinkId(),
                response.getStatus().name(), response.getCheckoutUrl(), response.getQrCode(), null);
    }

    @Override
    public Link cancel(long orderCode) {
        return convert(client.paymentRequests().cancel(orderCode, "Order cancelled"));
    }

    private Link convert(PaymentLink response) {
        String reference = response.getTransactions() == null ? null : response.getTransactions().stream()
                .map(t -> t.getReference()).filter(r -> r != null && !r.isBlank()).findFirst().orElse(null);
        // GET returns the link ID but no checkoutUrl/QR; recover its hosted checkout after a lost POST response.
        return new Link(response.getOrderCode(), response.getAmount(), response.getAmountPaid(),
                response.getId(), response.getStatus().name(),
                "https://pay.payos.vn/web/" + response.getId(), null, reference);
    }

    @Override
    public WebhookData verify(Map<String, Object> webhook) {
        return client.webhooks().verify(webhook);
    }
}
