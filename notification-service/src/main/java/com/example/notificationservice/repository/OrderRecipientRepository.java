package com.example.notificationservice.repository;

import com.example.notificationservice.model.OrderRecipient;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderRecipientRepository extends MongoRepository<OrderRecipient, String> {}
