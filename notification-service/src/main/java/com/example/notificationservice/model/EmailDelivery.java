package com.example.notificationservice.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document("email_deliveries")
public class EmailDelivery {
    @Id private String id;
    @Indexed(unique = true) private String eventId;
    private String eventType;
    private String orderId;
    private String customerId;
    private String email;
    private String verificationUrl;
    private LocalDateTime verificationExpiresAt;
    private String status;
    private int attempts;
    private LocalDateTime createdAt;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime lockedUntil;
    private String leaseToken;
    private String lastError;
}
