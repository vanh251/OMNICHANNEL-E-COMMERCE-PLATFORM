package com.example.identityservice.service;

import com.example.identityservice.entity.Role;
import com.example.identityservice.entity.User;
import com.example.identityservice.repository.EmailVerificationTokenRepository;
import com.example.identityservice.repository.IdentityOutboxRepository;
import com.example.identityservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EmailVerificationIntegrationTest {
    @Autowired private EmailVerificationService verification;
    @Autowired private UserRepository users;
    @Autowired private EmailVerificationTokenRepository tokens;
    @Autowired private IdentityOutboxRepository outbox;
    @Autowired private TransactionTemplate transactions;

    @BeforeEach
    void clean() {
        outbox.deleteAll();
        tokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void verificationIsSingleUseAndPublishesBothEvents() throws Exception {
        User user = users.save(User.builder().firstName("Mai").lastName("Tran")
                .email("mai@example.test").password("hashed").role(Role.USER).enabled(true).build());
        verification.requestVerification(user);
        assertThatThrownBy(() -> verification.resend(user.getEmail()))
                .isInstanceOf(com.example.identityservice.exception.VerificationRateLimitException.class);
        var requested = outbox.findAll().get(0);
        java.util.List<com.example.identityservice.entity.IdentityOutboxEvent> pending =
                transactions.execute(status -> outbox.findPending(PageRequest.of(0, 10)));
        assertThat(pending).hasSize(1);
        var event = new ObjectMapper().readTree(requested.getPayload());
        assertThat(event.path("eventType").asText()).isEqualTo("EmailVerificationRequestedEvent");
        String url = event.path("data").path("verificationUrl").asText();
        String raw = url.substring(url.indexOf("token=") + 6);
        assertThat(raw).hasSize(43);
        assertThat(tokens.findAll().get(0).getTokenHash()).doesNotContain(raw);

        verification.verify(raw);
        assertThat(users.findById(user.getId()).orElseThrow().getEmailVerifiedAt()).isNotNull();
        assertThat(outbox.findAll()).hasSize(2);
        assertThatThrownBy(() -> verification.verify(raw)).isInstanceOf(IllegalArgumentException.class);
        assertThat(outbox.findAll()).hasSize(2);
    }
}
