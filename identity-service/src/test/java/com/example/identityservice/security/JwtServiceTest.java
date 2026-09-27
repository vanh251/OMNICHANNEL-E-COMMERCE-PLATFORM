package com.example.identityservice.security;

import com.example.identityservice.entity.Role;
import com.example.identityservice.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtAccessExpiration", 900000L); // 15 mins
        ReflectionTestUtils.setField(jwtService, "jwtRefreshExpiration", 604800000L); // 7 days

        User user = User.builder()
                .id(1L)
                .email("vietanh@gmail.com")
                .password("encoded")
                .role(Role.USER)
                .enabled(true)
                .build();
        userDetails = new CustomUserDetails(user);
    }

    @Test
    @DisplayName("8. JWT hợp lệ → authenticated & claims extracted")
    void testValidJwt() {
        String token = jwtService.generateAccessToken(userDetails, "USER", 1L);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractEmail(token)).isEqualTo("vietanh@gmail.com");
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @Test
    @DisplayName("9. JWT hết hạn → rejected with ExpiredJwtException")
    void testExpiredJwt() {
        // Build token that expired 1 second ago
        String expiredToken = jwtService.buildToken(new HashMap<>(), "vietanh@gmail.com", -1000L);

        assertThatThrownBy(() -> jwtService.extractEmail(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("10. JWT không hợp lệ → rejected with JwtException")
    void testInvalidJwt() {
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalidPayload.invalidSignature";

        assertThatThrownBy(() -> jwtService.extractEmail(invalidToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("JWT valid check with mismatched user details returns false")
    void testJwtMismatchedUser() {
        String token = jwtService.generateAccessToken(userDetails, "USER", 1L);

        User otherUser = User.builder()
                .id(2L)
                .email("other@gmail.com")
                .password("encoded")
                .role(Role.USER)
                .enabled(true)
                .build();
        CustomUserDetails otherDetails = new CustomUserDetails(otherUser);

        assertThat(jwtService.isTokenValid(token, otherDetails)).isFalse();
    }
}
