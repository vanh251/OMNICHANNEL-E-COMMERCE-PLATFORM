package com.example.notificationservice.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("verified_emails")
public class VerifiedEmail {
    @Id private String id;
    private String customerId;
    private String email;
}
