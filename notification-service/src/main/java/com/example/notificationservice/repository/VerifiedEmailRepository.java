package com.example.notificationservice.repository;

import com.example.notificationservice.model.VerifiedEmail;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VerifiedEmailRepository extends MongoRepository<VerifiedEmail, String> {}
