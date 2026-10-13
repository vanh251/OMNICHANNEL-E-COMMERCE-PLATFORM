package com.example.paymentservice.controller;

import com.example.paymentservice.PostgresTestDatabase;
import com.example.paymentservice.TestTokens;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.repository.PaymentOrderStateRepository;
import com.example.paymentservice.repository.OutboxEventRepository;
import com.example.paymentservice.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.Map;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class PaymentControllerIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) { PostgresTestDatabase.configure(registry); }

    @Autowired WebApplicationContext context;
    @Autowired PaymentRepository payments;
    @Autowired PaymentOrderStateRepository states;
    @Autowired OutboxEventRepository outbox;
    @Autowired PaymentService service;
    MockMvc mvc;
    Payment payment;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        outbox.deleteAll();
        payments.deleteAll();
        states.deleteAll();
        payment = payments.save(Payment.builder().orderId("100").customerId(1L)
                .amount(new BigDecimal("1000")).currency("VND").paymentMethod("MOCK")
                .status(PaymentStatus.SUCCESS).transactionId("TXN-test").build());
    }

    @Test
    void requiresAValidAccessToken() throws Exception {
        mvc.perform(get("/api/payments/100")).andExpect(status().isUnauthorized());
        String secret = "payment-test-secret-at-least-32-bytes-long";
        for (String invalid : new String[]{"bad-token",
                TestTokens.signed(Map.of("userId", 1L, "role", "USER"), -1000, secret),
                TestTokens.signed(Map.of(), 60000, secret),
                TestTokens.signed(Map.of("userId", 1L, "role", "ADMIN"), 60000,
                        "untrusted-secret-at-least-32-bytes-long")}) {
            mvc.perform(get("/api/payments/100").header("Authorization", "Bearer " + invalid))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void ownerAndAdminCanReadButAnotherCustomerCannot() throws Exception {
        mvc.perform(get("/api/payments/100").header("Authorization", bearer(1, "USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value("100"));
        mvc.perform(get("/api/payments/100").header("Authorization", bearer(2, "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/payments/100").header("Authorization", bearer(2, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void historicalPaymentWithoutOwnerIsAdminOnly() throws Exception {
        payment.setCustomerId(null);
        payments.save(payment);
        mvc.perform(get("/api/payments/100").header("Authorization", bearer(1, "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/payments/100").header("Authorization", bearer(2, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void creationIsClosedEvenForAdmin() throws Exception {
        for (String role : new String[]{"USER", "ADMIN"}) {
            mvc.perform(post("/api/payments").header("Authorization", bearer(1, role))
                            .contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void refundRequiresAdminAndCancellationAndRetriesAreIdempotent() throws Exception {
        String path = "/api/payments/" + payment.getId() + "/refund";
        mvc.perform(post(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization", bearer(1, "USER"))).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", bearer(2, "ADMIN"))).andExpect(status().isConflict());
        service.cancelPayment("100");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(path).header("Authorization", bearer(2, "ADMIN")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, outbox.count());
    }

    private String bearer(long id, String role) { return "Bearer " + TestTokens.token(id, role); }
}
