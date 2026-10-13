package com.example.paymentservice.security;

public record JwtUserPrincipal(Long userId, String email, String role) {
    public boolean isAdmin() { return "ADMIN".equals(role); }
}
