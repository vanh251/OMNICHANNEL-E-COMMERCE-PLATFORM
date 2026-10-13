package com.example.paymentservice.payos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class SdkPayosGatewayTest {
    private static final String KEY = "checksum-test-key";
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<Map<String, Object>> response = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> requestBody = new AtomicReference<>();
    private final AtomicReference<String> clientId = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    HttpServer server;
    PayOS client;
    SdkPayosGateway gateway;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            clientId.set(exchange.getRequestHeaders().getFirst("x-client-id"));
            apiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            byte[] input = exchange.getRequestBody().readAllBytes();
            if (input.length > 0) requestBody.set(mapper.readValue(input, Map.class));
            byte[] output = mapper.writeValueAsBytes(response.get());
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, output.length);
            exchange.getResponseBody().write(output);
            exchange.close();
        });
        server.start();
        client = new PayOS(ClientOptions.builder().clientId("test-client").apiKey("test-api").checksumKey(KEY)
                .baseURL("http://127.0.0.1:" + server.getAddress().getPort())
                .maxRetries(0).timeoutMs(2000).logLevel(ClientOptions.LogLevel.NONE).build());
        gateway = new SdkPayosGateway(client, "https://example.test/return", "https://example.test/cancel");
    }

    @AfterEach
    void cleanup() {
        client.close();
        server.stop(0);
    }

    private String sign(String input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }

    private String signFields(Map<String, Object> data) throws Exception {
        List<String> fields = new ArrayList<>();
        new TreeMap<>(data).forEach((key, value) -> fields.add(key + "=" + value));
        return sign(String.join("&", fields));
    }

    @Test
    void createUsesChannelHeadersWholeVndAndOfficialRequestSignature() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bin", "970422"); data.put("accountNumber", "123"); data.put("accountName", "TEST");
        data.put("amount", 2000L); data.put("description", "DH123"); data.put("orderCode", 123L);
        data.put("currency", "VND"); data.put("paymentLinkId", "link");
        data.put("status", "PENDING"); data.put("checkoutUrl", "https://pay.payos.vn/web/link");
        data.put("qrCode", "qr");
        response.set(Map.of("code", "00", "desc", "success", "data", data,
                "signature", client.getCrypto().createSignatureFromObj(data, KEY)));
        var result = gateway.create(123, 2000, 1900000000);
        assertThat(result.checkoutUrl()).isEqualTo("https://pay.payos.vn/web/link");
        assertThat(clientId.get()).isEqualTo("test-client");
        assertThat(apiKey.get()).isEqualTo("test-api");
        assertThat(requestBody.get().get("signature")).isEqualTo(sign(
                "amount=2000&cancelUrl=https://example.test/cancel&description=DH123&orderCode=123&returnUrl=https://example.test/return"));
        assertThat(requestBody.get().get("expiredAt").toString()).isEqualTo("1900000000");
    }

    @Test
    void notFoundDoesNotHideAuthenticationOrProviderFailures() {
        for (String code : List.of("101", "231")) {
            response.set(Map.of("code", code, "desc", "not found"));
            assertThat(gateway.find(123)).isEmpty();
        }
        response.set(Map.of("code", "401", "desc", "invalid credentials"));
        assertThatThrownBy(() -> gateway.find(123)).isInstanceOf(vn.payos.exception.APIException.class);
    }

    @Test
    void webhookSignatureRejectsTamperingAndAcceptsIndependentHmac() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", 123L); data.put("amount", 2000L); data.put("description", "DH123");
        data.put("accountNumber", "123"); data.put("reference", "REF");
        data.put("transactionDateTime", "2026-10-13 12:00:00");
        data.put("currency", "VND"); data.put("paymentLinkId", "link"); data.put("code", "00");
        data.put("desc", "success");
        for (String field : List.of("counterAccountBankId", "counterAccountBankName", "counterAccountName",
                "counterAccountNumber", "virtualAccountName", "virtualAccountNumber")) data.put(field, "");
        Map<String, Object> webhook = new HashMap<>(Map.of("code", "00", "desc", "success", "success", true,
                "data", data, "signature", signFields(data)));
        assertThat(gateway.verify(webhook).getAmount()).isEqualTo(2000);
        data.put("amount", 1L);
        assertThatThrownBy(() -> gateway.verify(webhook)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> gateway.verify(Map.of())).isInstanceOf(RuntimeException.class);
    }

    @Test
    void forgedProviderResponseSignatureIsRejected() {
        response.set(Map.of("code", "00", "desc", "success",
                "data", Map.of("id", "link", "orderCode", 123, "amount", 2000, "status", "PAID"),
                "signature", "invalid"));
        assertThatThrownBy(() -> gateway.find(123)).isInstanceOf(RuntimeException.class);
    }
}
