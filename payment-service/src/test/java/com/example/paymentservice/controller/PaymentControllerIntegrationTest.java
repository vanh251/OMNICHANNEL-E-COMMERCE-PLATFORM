package com.example.paymentservice.controller;

import com.example.paymentservice.dto.PaymentRequest;
import com.example.paymentservice.dto.PaymentRequest;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

@SpringBootTest
@ActiveProfiles("test")
public class PaymentControllerIntegrationTest {

    @Autowired
    private PaymentController paymentController;

    @Autowired
    private PaymentRepository paymentRepository;



    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    @Test
    void testCreatePaymentSuccess() throws Exception {
        PaymentRequest request = new PaymentRequest();
        request.setOrderId("ORD-INT-1");
        request.setAmount(new BigDecimal("5000.00"));
        request.setCurrency("VND");
        request.setPaymentMethod("CREDIT_CARD");

        ResponseEntity<?> response = paymentController.createPayment(request);

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        com.example.paymentservice.dto.PaymentResponse body = (com.example.paymentservice.dto.PaymentResponse) response.getBody();
        org.junit.jupiter.api.Assertions.assertEquals("ORD-INT-1", body.getOrderId());
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.SUCCESS, body.getStatus());
    }

    @Test
    void testCreatePaymentFailedDueToAmount() {
        PaymentRequest request = new PaymentRequest();
        request.setOrderId("ORD-INT-2");
        request.setAmount(new BigDecimal("15000.00"));
        request.setCurrency("VND");
        request.setPaymentMethod("CREDIT_CARD");

        ResponseEntity<?> response = paymentController.createPayment(request);

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        com.example.paymentservice.dto.PaymentResponse body = (com.example.paymentservice.dto.PaymentResponse) response.getBody();
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.FAILED, body.getStatus());
    }

    @Test
    void testGetPaymentByOrderId() throws Exception {
        Payment payment = Payment.builder()
                .orderId("ORD-INT-3")
                .amount(new BigDecimal("1000.00"))
                .currency("VND")
                .paymentMethod("CREDIT_CARD")
                .status(PaymentStatus.SUCCESS)
                .transactionId("TXN-123")
                .build();
        paymentRepository.save(payment);

        ResponseEntity<?> response = paymentController.getPaymentByOrderId("ORD-INT-3");

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        com.example.paymentservice.dto.PaymentResponse body = (com.example.paymentservice.dto.PaymentResponse) response.getBody();
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.SUCCESS, body.getStatus());
    }

    @Test
    void testGetPaymentNotFound() {
        org.junit.jupiter.api.Assertions.assertThrows(com.example.paymentservice.exception.PaymentNotFoundException.class, () -> {
            paymentController.getPaymentByOrderId("ORD-NOT-FOUND");
        });
    }

    @Test
    void testRefundPaymentSuccess() {
        Payment payment = Payment.builder()
                .orderId("ORD-INT-4")
                .amount(new BigDecimal("1000.00"))
                .currency("VND")
                .paymentMethod("CREDIT_CARD")
                .status(PaymentStatus.SUCCESS)
                .transactionId("TXN-123")
                .build();
        payment = paymentRepository.save(payment);

        ResponseEntity<?> response = paymentController.refundPayment(payment.getId());

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        com.example.paymentservice.dto.PaymentResponse body = (com.example.paymentservice.dto.PaymentResponse) response.getBody();
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.REFUNDED, body.getStatus());
    }
}
