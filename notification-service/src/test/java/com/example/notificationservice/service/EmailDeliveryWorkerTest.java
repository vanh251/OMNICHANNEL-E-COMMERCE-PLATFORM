package com.example.notificationservice.service;

import com.example.notificationservice.model.EmailDelivery;
import com.example.notificationservice.model.OrderRecipient;
import com.example.notificationservice.repository.OrderRecipientRepository;
import com.example.notificationservice.repository.VerifiedEmailRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryWorkerTest {
    @Mock private MongoTemplate mongo;
    @Mock private OrderRecipientRepository recipients;
    @Mock private VerifiedEmailRepository verified;
    @Mock private JavaMailSender mail;
    @InjectMocks private EmailDeliveryWorker worker;

    @Test
    void waitsForRecipientAndVerificationBeforeSendingUtf8Email() throws Exception {
        ReflectionTestUtils.setField(worker, "from", "no-reply@example.test");
        EmailDelivery job = new EmailDelivery();
        job.setId("job-1");
        job.setLeaseToken("lease-1");
        job.setEventId("event-1");
        job.setEventType("OrderShippedEvent");
        job.setOrderId("42");
        job.setCreatedAt(LocalDateTime.now());
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(EmailDelivery.class))).thenReturn(job, null, job, null, job, null);
        when(recipients.findById("42")).thenReturn(Optional.empty(), Optional.of(recipient()));
        when(verified.existsById("7:customer@example.test")).thenReturn(false, true);

        worker.run();
        worker.run();
        verifyNoInteractions(mail);

        when(mail.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        worker.run();

        var sent = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail).send(sent.capture());
        assertThat(sent.getValue().getAllRecipients()[0].toString()).isEqualTo("customer@example.test");
        assertThat(sent.getValue().getSubject()).contains("42").contains("đang giao");
        assertThat(sent.getValue().getContent().toString()).contains("đang được giao");
        verify(mongo, times(3)).updateFirst(any(Query.class), any(Update.class), eq(EmailDelivery.class));
    }

    private OrderRecipient recipient() {
        OrderRecipient result = new OrderRecipient();
        result.setOrderId("42");
        result.setCustomerId("7");
        result.setEmail("customer@example.test");
        return result;
    }
}
