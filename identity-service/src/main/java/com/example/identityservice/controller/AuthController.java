package com.example.identityservice.controller;

import com.example.identityservice.dto.AuthResponse;
import com.example.identityservice.dto.LoginRequest;
import com.example.identityservice.dto.RegisterRequest;
import com.example.identityservice.dto.UserResponse;
import com.example.identityservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and user registration APIs")
public class AuthController {

    private final AuthService authService;
    private final com.example.identityservice.service.EmailVerificationService emailVerificationService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user", description = "Creates a new user account with default role USER")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticates user credentials and returns JWT access & refresh tokens")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend the email verification link")
    public ResponseEntity<Void> resendVerification(Authentication authentication) {
        emailVerificationService.resend(authentication.getName());
        return ResponseEntity.accepted().build();
    }

    @GetMapping(value = "/verify-email", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Verify an email address from its one-time link")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        try {
            emailVerificationService.verify(token);
            return ResponseEntity.ok().header("Referrer-Policy", "no-referrer")
                    .header("Cache-Control", "no-store")
                    .body("<html><body><h1>Email da duoc xac minh</h1></body></html>");
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().header("Referrer-Policy", "no-referrer")
                    .header("Cache-Control", "no-store")
                    .body("<html><body><h1>Link khong hop le hoac da het han</h1><p>Hay dang nhap va yeu cau gui lai email xac minh.</p></body></html>");
        }
    }
}
