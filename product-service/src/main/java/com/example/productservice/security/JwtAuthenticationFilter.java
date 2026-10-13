package com.example.productservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final Key key;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        // Same HS256 key as Identity. Fail startup instead of using a built-in secret.
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var jwt = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(header.substring(7));
                Claims claims = jwt.getBody();
                Object userId = claims.get("userId");
                String role = claims.get("role", String.class);
                if (!"HS256".equals(jwt.getHeader().getAlgorithm()) || claims.getExpiration() == null
                        || claims.getSubject() == null || claims.getSubject().isBlank()
                        || !(userId instanceof Number) || ((Number) userId).longValue() <= 0
                        || !("USER".equals(role) || "ADMIN".equals(role))) {
                    throw new IllegalArgumentException("Invalid access token claims");
                }
                var principal = new JwtUserPrincipal(((Number) userId).longValue(), claims.getSubject(), role);
                var auth = new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth);
                SecurityContextHolder.setContext(context);
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
