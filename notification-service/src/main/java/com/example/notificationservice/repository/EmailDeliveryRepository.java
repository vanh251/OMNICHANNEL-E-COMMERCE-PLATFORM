package com.example.notificationservice.repository;

import com.example.notificationservice.model.EmailDelivery;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmailDeliveryRepository extends MongoRepository<EmailDelivery, String> {
    long countByStatus(String status);
}
