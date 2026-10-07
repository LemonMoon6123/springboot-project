package com.scaffold.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtUtils(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long userId, String username, String role, String accountType) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder() // 创建Token令牌
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .claim("accountType", accountType)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Long getUserId(String token) {
        Object value = parseClaims(token).get("userId");
        if (value instanceof Integer intVal) {
            return intVal.longValue();
        }
        if (value instanceof Long longVal) {
            return longVal;
        }
        return null;
    }

    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public String getAccountType(String token) {
        String accountType = parseClaims(token).get("accountType", String.class);
        if (accountType != null) {
            return accountType;
        }
        // Backward compatibility for older tokens
        return "ADMIN".equals(getRole(token)) ? "ADMIN" : "USER";
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
