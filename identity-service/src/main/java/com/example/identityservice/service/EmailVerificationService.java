package com.example.identityservice.service;

import com.example.identityservice.entity.EmailVerificationToken;
import com.example.identityservice.entity.User;
import com.example.identityservice.exception.VerificationRateLimitException;
import com.example.identityservice.repository.EmailVerificationTokenRepository;
import com.example.identityservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final IdentityEventPublisher eventPublisher;
    private final SecureRandom random = new SecureRandom();

    @Value("${email.verification-base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public void requestVerification(User user) {
        if (user.getEmailVerifiedAt() != null) return;
        LocalDateTime now = LocalDateTime.now();
        if (tokenRepository.countByUserIdAndCreatedAtAfter(user.getId(), now.minusDays(1)) >= 5
                || tokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                    .map(token -> token.getCreatedAt().isAfter(now.minusMinutes(1))).orElse(false)) {
            throw new VerificationRateLimitException();
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(user.getId());
        token.setTokenHash(hash(raw));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plusHours(24));
        tokenRepository.save(token);
        eventPublisher.publish("EmailVerificationRequestedEvent", "identity.email.verification.requested",
                Map.of("customerId", user.getId().toString(), "email", user.getEmail(),
                        "verificationUrl", baseUrl + "/api/auth/verify-email?token=" + raw,
                        "expiresAt", token.getExpiresAt().toString()));
    }

    @Transactional
    public void resend(String email) {
        User found = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        User user = userRepository.findByIdForUpdate(found.getId()).orElseThrow();
        requestVerification(user);
    }

    @Transactional
    public void verify(String raw) {
        if (raw == null || raw.length() != 43) throw new IllegalArgumentException("Invalid verification link");
        EmailVerificationToken token = tokenRepository.findByTokenHashForUpdate(hash(raw))
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification link"));
        LocalDateTime now = LocalDateTime.now();
        if (token.getConsumedAt() != null || !now.isBefore(token.getExpiresAt())) {
            throw new IllegalArgumentException("Verification link expired or already used");
        }
        User user = userRepository.findByIdForUpdate(token.getUserId()).orElseThrow();
        token.setConsumedAt(now);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
            eventPublisher.publish("CustomerEmailVerifiedEvent", "identity.email.verified",
                    Map.of("customerId", user.getId().toString(), "email", user.getEmail(),
                            "verifiedAt", now.toString()));
        }
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
