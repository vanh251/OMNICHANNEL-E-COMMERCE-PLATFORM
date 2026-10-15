package com.example.notificationservice.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("order_email_recipients")
public class OrderRecipient {
    @Id private String orderId;
    private String customerId;
    private String email;
}
