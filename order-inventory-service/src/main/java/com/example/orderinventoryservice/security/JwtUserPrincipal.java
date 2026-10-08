package com.example.orderinventoryservice.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Lightweight principal object carrying user identity from JWT claims.
 * Used as the Authentication principal in SecurityContext so controllers
 * can extract userId without re-parsing the JWT.
 */
@Getter
@AllArgsConstructor
public class JwtUserPrincipal {

    private final Long userId;
    private final String email;
    private final String role;

    @Override
    public String toString() {
        return email;
    }
}
