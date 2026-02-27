package com.openforge.aimate.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long      expirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key          = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** Generate a signed JWT containing userId and username as claims. */
    public String generate(Long userId, String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(key)
                .compact();
    }

    /** Extract all claims from a valid token. Throws JwtException if invalid. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Returns userId if the token is valid, else null. */
    public Long extractUserId(String token) {
        try {
            return Long.valueOf(parse(token).getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[JWT] Invalid token: {}", e.getMessage());
            return null;
        }
    }

    /** Returns username claim if the token is valid, else null. */
    public String extractUsername(String token) {
        try {
            return parse(token).get("username", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
