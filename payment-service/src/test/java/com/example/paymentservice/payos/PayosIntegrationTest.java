package com.example.paymentservice.payos;

import com.example.paymentservice.PostgresTestDatabase;
import com.example.paymentservice.TestTokens;
import com.example.paymentservice.dto.PaymentRequest;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"payment.provider=payos", "payos.worker-enabled=false",
        "payos.client-id=test-client", "payos.api-key=test-api",
        "payos.checksum-key=test-checksum-key"})
@ActiveProfiles("test")
class PayosIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { PostgresTestDatabase.configure(registry); }
    @Autowired PaymentService service;
    @Autowired PaymentRepository payments;
    @Autowired PaymentOrderStateRepository states;
    @Autowired OutboxEventRepository outbox;
    @Autowired PayosWorker worker;
    @Autowired WebApplicationContext context;
    @MockitoBean PayosGateway gateway;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        outbox.deleteAll();
        payments.deleteAll();
        states.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private Payment create(String id) {
        PaymentRequest request = new PaymentRequest();
        request.setOrderId(id);
        request.setCustomerId(42L);
        request.setAmount(new BigDecimal("2000"));
        request.setCurrency("VND");
        request.setPaymentMethod("MOCK");
        service.createPayment(request);
        return payments.findByOrderId(id).orElseThrow();
    }

    private PayosGateway.Link link(Payment p, String status) {
        return new PayosGateway.Link(p.getProviderOrderCode(), 2000L,
                "PAID".equals(status) ? 2000L : 0L, "link-" + p.getId(), status,
                "https://pay.payos.vn/web/link-" + p.getId(), "qr", "reference-" + p.getId());
    }

    private WebhookData webhook(Payment p, long amount) {
        WebhookData data = new WebhookData();
        data.setOrderCode(p.getProviderOrderCode());
        data.setAmount(amount);
        data.setCurrency("VND");
        data.setCode("00");
        data.setReference("reference-" + p.getId());
        data.setPaymentLinkId("link-" + p.getId());
        return data;
    }

    @Test
    void intentIsPendingAndCommittedBeforeProviderCallsAndReplayKeepsSameCode() {
        Payment first = create("intent");
        Payment second = create("intent");
        assertThat(first.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(second.getProviderOrderCode()).isEqualTo(first.getProviderOrderCode());
        assertThat(first.getProviderOrderCode()).isBetween(1L, (1L << 53) - 1);
        assertThat(outbox.count()).isZero();
        verifyNoInteractions(gateway);
    }

    @Test
    void workerCreatesCheckoutAndAuthenticatedOwnerCanReadIt() throws Exception {
        Payment p = create("checkout");
        when(gateway.find(p.getProviderOrderCode())).thenReturn(Optional.empty());
        when(gateway.create(p.getProviderOrderCode(), 2000, p.getExpiresAt())).thenReturn(link(p, "PENDING"));
        worker.reconcile(p.getId());
        mvc.perform(get("/api/payments/checkout").header("Authorization", "Bearer " + TestTokens.token(42, "USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.checkoutUrl").value("https://pay.payos.vn/web/link-" + p.getId()))
                .andExpect(jsonPath("$.qrCode").value("qr"));
        assertThat(outbox.count()).isZero();
    }

    @Test
    void lostCreateResponseRecoversPersistedOrderCodeInsteadOfCreatingAnotherLink() {
        Payment p = create("timeout");
        when(gateway.find(p.getProviderOrderCode())).thenReturn(Optional.empty());
        when(gateway.create(p.getProviderOrderCode(), 2000, p.getExpiresAt()))
                .thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(() -> worker.reconcile(p.getId())).isInstanceOf(IllegalStateException.class);
        when(gateway.find(p.getProviderOrderCode())).thenReturn(Optional.of(link(p, "PENDING")));
        worker.reconcile(p.getId());
        verify(gateway, times(1)).create(p.getProviderOrderCode(), 2000, p.getExpiresAt());
        assertThat(payments.findByOrderId("timeout").orElseThrow().getPaymentLinkId()).isNotNull();
    }

    @Test
    void outageCannotDeclarePaymentFailedOrConfirmOrder() {
        Payment p = create("outage");
        when(gateway.find(p.getProviderOrderCode())).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> worker.reconcile(p.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(payments.findById(p.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void paidReconciliationPublishesSuccessExactlyOnceAndNeverDowngrades() {
        Payment p = create("paid");
        when(gateway.find(p.getProviderOrderCode())).thenReturn(Optional.of(link(p, "PAID")));
        worker.reconcile(p.getId());
        service.applyPayosLink(p.getId(), link(p, "PENDING"));
        service.applyPayosWebhook(webhook(p, 2000));
        assertThat(payments.findById(p.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(outbox.findAll()).extracting(e -> e.getRoutingKey()).containsExactly("payment.succeeded");
    }

    @Test
    void cancellationClosesRemoteLinkAndPublishesFailureOnce() {
        Payment p = create("cancel");
        service.cancelPayment("cancel");
        when(gateway.find(p.getProviderOrderCode())).thenReturn(Optional.of(link(p, "PENDING")));
        when(gateway.cancel(p.getProviderOrderCode())).thenReturn(link(p, "CANCELLED"));
        worker.reconcile(p.getId());
        service.applyPayosLink(p.getId(), link(p, "CANCELLED"));
        assertThat(payments.findById(p.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(outbox.findAll()).extracting(e -> e.getRoutingKey()).containsExactly("payment.failed");
    }

    @Test
    void expiredOrCancelledIntentWithoutRemoteLinkCannotCreateCheckout() {
        Payment p = create("before");
        service.cancelPayment("before");
        when(gateway.find(p.getProviderOrderCode())).thenReturn(Optional.empty());
        worker.reconcile(p.getId());
        verify(gateway, never()).create(anyLong(), anyLong(), anyLong());
        assertThat(payments.findById(p.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void lateRealFundsRequireReconciliationAndCannotBeFalselyRefunded() {
        Payment p = create("late");
        service.cancelPayment("late");
        service.applyPayosWebhook(webhook(p, 2000));
        service.applyPayosWebhook(webhook(p, 2000));
        Payment paid = payments.findById(p.getId()).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(paid.isRefundRequired()).isTrue();
        assertThatThrownBy(() -> service.refundPayment(p.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(outbox.findAll()).extracting(e -> e.getRoutingKey()).containsExactly("payment.succeeded");
    }

    @Test
    void concurrentDuplicateWebhooksProduceOneSuccessEvent() throws Exception {
        Payment p = create("parallel");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CyclicBarrier gate = new CyclicBarrier(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                gate.await(10, TimeUnit.SECONDS);
                service.applyPayosWebhook(webhook(p, 2000));
                return null;
            }));
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(payments.findById(p.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void partialPaymentAndMismatchedLinkCannotConfirmOrder() {
        Payment p = create("partial");
        service.applyPayosLink(p.getId(), link(p, "PENDING"));
        service.applyPayosWebhook(webhook(p, 1000));
        WebhookData wrong = webhook(p, 2000);
        wrong.setPaymentLinkId("another-link");
        assertThatThrownBy(() -> service.applyPayosWebhook(wrong)).isInstanceOf(IllegalArgumentException.class);
        wrong.setPaymentLinkId("link-" + p.getId());
        wrong.setCurrency("USD");
        assertThatThrownBy(() -> service.applyPayosWebhook(wrong)).isInstanceOf(IllegalArgumentException.class);
        var underpaid = new PayosGateway.Link(p.getProviderOrderCode(), 2000L, 1000L,
                "link-" + p.getId(), "PAID", null, null, "ref");
        assertThatThrownBy(() -> service.applyPayosLink(p.getId(), underpaid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(payments.findById(p.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void invalidWholeVndAmountCancelsSagaInsteadOfHoldingInventoryForever() {
        PaymentRequest request = new PaymentRequest();
        request.setOrderId("fractional");
        request.setCustomerId(42L);
        request.setAmount(new BigDecimal("2000.50"));
        request.setCurrency("VND");
        request.setPaymentMethod("MOCK");
        assertThat(service.createPayment(request).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(outbox.findAll()).extracting(e -> e.getRoutingKey()).containsExactly("payment.failed");
        verifyNoInteractions(gateway);
    }

    @Test
    void publicWebhookRequiresSignatureButReturnQueryCannotConfirmPayment() throws Exception {
        Payment p = create("http");
        when(gateway.verify(any())).thenThrow(new IllegalArgumentException("bad signature"));
        mvc.perform(post("/api/payments/webhooks/payos").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/payments/payos/return?orderCode=" + p.getProviderOrderCode() + "&status=PAID"))
                .andExpect(status().isOk());
        assertThat(outbox.count()).isZero();
        doReturn(webhook(p, 2000)).when(gateway).verify(any());
        mvc.perform(post("/api/payments/webhooks/payos").contentType("application/json").content("{}"))
                .andExpect(status().isOk());
        assertThat(outbox.count()).isEqualTo(1);
        WebhookData probe = webhook(p, 2000);
        probe.setOrderCode(123L);
        doReturn(probe).when(gateway).verify(any());
        mvc.perform(post("/api/payments/webhooks/payos").contentType("application/json").content("{}"))
                .andExpect(status().isOk());
        assertThat(payments.count()).isEqualTo(1);
    }
}
