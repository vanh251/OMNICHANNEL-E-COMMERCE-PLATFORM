package com.example.identityservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret:vanhdzvcldiojfijsiodjfojsiodjfijnenjbtuututjdnjkfn}")
    private String secretKey;

    @Value("${jwt.access-expiration:${jwt.access-token-expiration:900000}}")
    private long jwtAccessExpiration;

    @Value("${jwt.refresh-expiration:${jwt.refresh-token-expiration:604800000}}")
    private long jwtRefreshExpiration;

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateAccessToken(UserDetails userDetails, String role, Long userId) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", role);
        extraClaims.put("userId", userId);
        return buildToken(extraClaims, userDetails.getUsername(), jwtAccessExpiration);
    }

    public String generateAccessToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails.getUsername(), jwtAccessExpiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails.getUsername(), jwtRefreshExpiration);
    }

    public String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        return (email.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey() {
        try {
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                keyBytes = sha.digest(keyBytes);
            }
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error initializing JWT signing key", e);
        }
    }
}
