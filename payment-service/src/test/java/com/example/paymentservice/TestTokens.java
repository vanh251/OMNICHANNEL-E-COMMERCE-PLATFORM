package com.example.paymentservice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public final class TestTokens {
    private TestTokens() {}
    public static String token(long userId, String role) {
        return signed(Map.of("userId", userId, "role", role), 60000, "payment-test-secret-at-least-32-bytes-long");
    }
    public static String signed(Map<String, Object> claims, long lifetime, String secret) {
        return Jwts.builder().setClaims(claims).setSubject("test@example.test")
                .setExpiration(new Date(System.currentTimeMillis() + lifetime))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }
}
