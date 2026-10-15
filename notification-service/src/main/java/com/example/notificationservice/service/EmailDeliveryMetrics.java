package com.example.notificationservice.service;

import com.example.notificationservice.repository.EmailDeliveryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class EmailDeliveryMetrics {
    private static final String[] STATUSES = {
            "PENDING", "WAITING", "SENDING", "RETRY", "SENT", "SKIPPED",
            "SUPPRESSED", "UNDELIVERABLE", "PAUSED_CONFIG", "DEAD"
    };

    public EmailDeliveryMetrics(MeterRegistry registry, EmailDeliveryRepository repository) {
        for (String status : STATUSES) {
            Gauge.builder("notification.email.jobs", repository,
                            jobs -> jobs.countByStatus(status))
                    .description("Number of email delivery jobs in each state")
                    .tag("status", status)
                    .register(registry);
        }
    }
}
