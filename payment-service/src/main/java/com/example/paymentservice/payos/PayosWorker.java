package com.example.paymentservice.payos;

import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.provider", havingValue = "payos")
public class PayosWorker {
    private final PaymentRepository payments;
    private final PaymentService service;
    private final PayosGateway gateway;

    @Value("${payos.worker-enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${payos.poll-ms:15000}", initialDelayString = "${payos.poll-ms:15000}")
    public void processPending() {
        if (!enabled) return;
        for (var payment : payments.findPayosPending(PaymentStatus.PENDING, PageRequest.of(0, 20))) {
            try {
                reconcile(payment.getId());
            } catch (RuntimeException ex) {
                // Provider failures stay retryable; never log SDK bodies, bank details or keys.
                log.warn("payOS synchronization will retry for payment id={} ({})",
                        payment.getId(), ex.getClass().getSimpleName());
            }
        }
    }

    public void reconcile(Long paymentId) {
        var task = service.payosTask(paymentId);
        if (task == null) return;
        var found = gateway.find(task.orderCode());
        if (found.isEmpty()) {
            if (task.cancelled() || task.expiresAt() <= Instant.now().getEpochSecond()) {
                service.failUncreatedPayosLink(paymentId);
                return;
            }
            // A timeout leaves the persisted code unchanged: next pass retrieves the same remote link.
            var created = gateway.create(task.orderCode(), task.amount(), task.expiresAt());
            if (service.applyPayosLink(paymentId, created)) {
                service.applyPayosLink(paymentId, gateway.cancel(task.orderCode()));
            }
            return;
        }
        var link = found.get();
        boolean shouldCancel = service.applyPayosLink(paymentId, link);
        if (shouldCancel) {
            // Read canonical state again if payment wins the cancellation race.
            try {
                service.applyPayosLink(paymentId, gateway.cancel(task.orderCode()));
            } catch (RuntimeException ex) {
                var latest = gateway.find(task.orderCode());
                if (latest.isPresent() && "PAID".equals(latest.get().status())) {
                    service.applyPayosLink(paymentId, latest.get());
                } else {
                    throw ex;
                }
            }
        }
    }
}
