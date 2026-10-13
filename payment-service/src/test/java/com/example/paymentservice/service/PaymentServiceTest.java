package com.example.paymentservice.service;

import com.example.paymentservice.dto.PaymentRequest;
import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.exception.PaymentNotFoundException;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.repository.OutboxEventRepository;
import com.example.paymentservice.entity.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private com.example.paymentservice.repository.PaymentOrderStateRepository orderStateRepository;

    @Mock
    private jakarta.validation.Validator validator;

    private com.example.paymentservice.entity.PaymentOrderState orderState;

    private final com.example.paymentservice.security.JwtUserPrincipal owner =
            new com.example.paymentservice.security.JwtUserPrincipal(1L, "owner@example.test", "USER");

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PaymentService paymentService;

    private PaymentRequest paymentRequest;
    private Payment payment;

    @BeforeEach
    void setUp() {
        orderState = new com.example.paymentservice.entity.PaymentOrderState();
        orderState.setOrderId("ORD-123");
        lenient().when(orderStateRepository.lockByOrderId("ORD-123")).thenReturn(orderState);
        paymentRequest = new PaymentRequest();
        paymentRequest.setCustomerId(1L);
        paymentRequest.setOrderId("ORD-123");
        paymentRequest.setAmount(new BigDecimal("150.00"));
        paymentRequest.setCurrency("VND");
        paymentRequest.setPaymentMethod("CREDIT_CARD");

        payment = Payment.builder()
                .id(1L)
                .orderId("ORD-123")
                .customerId(1L)
                .amount(new BigDecimal("150.00"))
                .currency("VND")
                .paymentMethod("CREDIT_CARD")
                .status(PaymentStatus.SUCCESS)
                .transactionId("TXN-abc")
                .build();
    }

    @Test
    void processPayment_Success() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.createPayment(paymentRequest);

        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertNotNull(response.getTransactionId());
        assertTrue(response.getTransactionId().startsWith("TXN-"));
        
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(outboxEventRepository).save(any());
        var captor = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertEquals("payment.succeeded", captor.getValue().getRoutingKey());
        assertTrue(captor.getValue().getPayload().contains("PaymentSucceededEvent"));
    }

    @Test
    void processPayment_Failed() {
        paymentRequest.setAmount(new BigDecimal("15000.00"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(2L);
            return p;
        });

        PaymentResponse response = paymentService.createPayment(paymentRequest);

        assertNotNull(response);
        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertNull(response.getTransactionId());
        
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(outboxEventRepository).save(any());
        var captor = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertEquals("payment.failed", captor.getValue().getRoutingKey());
        assertTrue(captor.getValue().getPayload().contains("PaymentFailedEvent"));
    }

    @Test
    void repeatedOrderCreatedDoesNotCreateAnotherPaymentOrEvent() {
        when(paymentRepository.findByOrderId("ORD-123")).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.createPayment(paymentRequest);

        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void getPaymentByOrderId_Success() {
        when(paymentRepository.findByOrderId("ORD-123")).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByOrderId("ORD-123", owner);

        assertNotNull(response);
        assertEquals("ORD-123", response.getOrderId());
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
    }

    @Test
    void getPaymentByOrderId_NotFound() {
        when(paymentRepository.findByOrderId("ORD-999")).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () -> {
            paymentService.getPaymentByOrderId("ORD-999", owner);
        });
    }

    @Test
    void refundPayment_Success() {
        orderState.setCancelled(true);
        when(paymentRepository.findOrderIdById(1L)).thenReturn(Optional.of("ORD-123"));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.refundPayment(1L);

        assertNotNull(response);
        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
    }

    @Test
    void refundPayment_FailedNotSuccess() {
        payment.setStatus(PaymentStatus.FAILED);
        orderState.setCancelled(true);
        when(paymentRepository.findOrderIdById(1L)).thenReturn(Optional.of("ORD-123"));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThrows(IllegalStateException.class, () -> {
            paymentService.refundPayment(1L);
        });
    }
}
