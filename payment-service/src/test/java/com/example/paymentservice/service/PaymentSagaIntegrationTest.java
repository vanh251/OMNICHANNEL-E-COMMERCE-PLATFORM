package com.example.paymentservice.service;

import com.example.paymentservice.PostgresTestDatabase;
import com.example.paymentservice.dto.PaymentRequest;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.repository.PaymentOrderStateRepository;
import com.example.paymentservice.repository.OutboxEventRepository;
import com.example.paymentservice.event.OrderCreatedConsumer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PaymentSagaIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) { PostgresTestDatabase.configure(registry); }
    @Autowired PaymentService service;
    @Autowired PaymentRepository payments;
    @Autowired PaymentOrderStateRepository states;
    @Autowired OutboxEventRepository outbox;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired OrderCreatedConsumer consumer;

    @BeforeEach
    void cleanup() {
        outbox.deleteAll();
        payments.deleteAll();
        states.deleteAll();
    }

    private PaymentRequest request(String orderId, String amount) {
        PaymentRequest request = new PaymentRequest();
        request.setOrderId(orderId);
        request.setCustomerId(42L);
        request.setAmount(new BigDecimal(amount));
        request.setCurrency("VND");
        request.setPaymentMethod("MOCK");
        return request;
    }

    @Test
    void cancellationBeforeCreationPreventsChargeEvenAfterRedelivery() {
        service.cancelPayment("early");
        service.cancelPayment("early");
        assertThat(service.createPayment(request("early", "1000")).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(service.createPayment(request("early", "1000")).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(payments.findByOrderId("early").orElseThrow().getTransactionId()).isNull();
        assertThat(outbox.count()).isZero();
    }

    @Test
    void cancellationAfterSuccessRefundsExactlyOnce() {
        var payment = service.createPayment(request("late", "1000"));
        service.cancelPayment("late");
        service.cancelPayment("late");
        service.refundPayment(payment.getId());
        assertThat(service.createPayment(request("late", "1000")).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(outbox.findAll()).extracting(e -> e.getRoutingKey())
                .containsExactlyInAnyOrder("payment.succeeded", "payment.refunded");
        assertThat(payments.findByOrderId("late").orElseThrow().getCustomerId()).isEqualTo(42L);
    }

    @Test
    void failedPaymentIsNotRefunded() {
        service.createPayment(request("declined", "10001"));
        service.cancelPayment("declined");
        assertThat(payments.findByOrderId("declined").orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(outbox.findAll()).extracting(e -> e.getRoutingKey()).containsExactly("payment.failed");
    }

    @Test
    void parallelDuplicateCreationProducesOneChargeAndOneEvent() throws Exception {
        parallel(8, () -> service.createPayment(request("duplicate", "1000")));
        assertThat(payments.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void simultaneousCreationAndCancellationNeverLeaveASuccessfulCharge() throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        parallel(8, () -> {
            if (count.getAndIncrement() % 2 == 0) service.createPayment(request("race", "1000"));
            else service.cancelPayment("race");
        });
        assertThat(payments.findByOrderId("race").orElseThrow().getStatus())
                .isIn(PaymentStatus.FAILED, PaymentStatus.REFUNDED);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(outbox.findAll().stream().filter(e -> e.getRoutingKey().equals("payment.refunded")).count())
                .isLessThanOrEqualTo(1);
    }

    @Test
    void simultaneousRefundRequestsPublishOneRefundEvent() throws Exception {
        var payment = service.createPayment(request("refund", "1000"));
        // Model a persisted cancellation awaiting compensation.
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                states.lockByOrderId("refund").setCancelled(true));
        parallel(8, () -> service.refundPayment(payment.getId()));
        assertThat(payments.findByOrderId("refund").orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(outbox.findAll()).extracting(e -> e.getRoutingKey())
                .containsExactlyInAnyOrder("payment.succeeded", "payment.refunded");
    }

    @Test
    void rollbackRestoresPaymentCancellationMarkerAndOutboxTogether() {
        service.createPayment(request("rollback", "1000"));
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            service.cancelPayment("rollback");
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(states.findById("rollback").orElseThrow().isCancelled()).isFalse();
        assertThat(payments.findByOrderId("rollback").orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void consumerMapsOwnerAndHandlesCancellationEnvelope() throws Exception {
        String created = """
                {"eventType":"OrderCreatedEvent","data":{"orderId":"event","customerId":"42",
                 "totalAmount":1000,"currency":"VND"}}
                """;
        consumer.receive(new Message(created.getBytes(StandardCharsets.UTF_8), new MessageProperties()));
        String cancelled = """
                {"eventType":"OrderCancelledEvent","data":{"orderId":"event"}}
                """;
        consumer.receive(new Message(cancelled.getBytes(StandardCharsets.UTF_8), new MessageProperties()));
        assertThat(payments.findByOrderId("event").orElseThrow().getCustomerId()).isEqualTo(42L);
        assertThat(payments.findByOrderId("event").orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    private void parallel(int workers, Runnable action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CyclicBarrier barrier = new CyclicBarrier(workers);
        try {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < workers; i++) futures.add(pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                action.run();
                return null;
            }));
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }
}
