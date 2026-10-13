package com.example.productservice.security;

public record JwtUserPrincipal(Long userId, String email, String role) {
    public boolean isAdmin() { return "ADMIN".equals(role); }
}
