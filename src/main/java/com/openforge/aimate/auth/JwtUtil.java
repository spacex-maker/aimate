package com.openforge.aimate.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    public static final String TYP_USER = "user";
    public static final String TYP_ADMIN = "admin";
    public static final String AUD_ADMIN = "chat-admin";
    /** 滑动续期：前端读取并替换本地 token */
    public static final String HEADER_NEW_TOKEN = "X-New-Token";

    private final SecretKey key;
    @Getter
    private final long expirationMs;
    @Getter
    private final long renewalThresholdMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:2592000000}") long expirationMs,
            @Value("${app.jwt.renewal-threshold-ms:604800000}") long renewalThresholdMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.renewalThresholdMs = Math.min(Math.max(60_000L, renewalThresholdMs), expirationMs);
    }

    /** Generate a signed JWT containing userId, username and role as claims. */
    public String generate(Long userId, String username, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("typ", TYP_USER)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(key)
                .compact();
    }

    /** 运营后台 JWT（admin_users），与用户会话隔离。 */
    public String generateAdmin(Long adminId, String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(adminId))
                .claim("typ", TYP_ADMIN)
                .claim("aud", AUD_ADMIN)
                .claim("email", email)
                .claim("role", "ADMIN")
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

    /** 剩余有效期是否低于阈值（应下发新 token）。 */
    public boolean shouldRenew(Claims claims) {
        Date exp = claims.getExpiration();
        if (exp == null) return true;
        long remaining = exp.getTime() - System.currentTimeMillis();
        return remaining > 0 && remaining < renewalThresholdMs;
    }

    /** 用既有 claims 重新签发完整有效期的 token（滑动续期）。 */
    public String renewFromClaims(Claims claims) {
        String typ = claims.get("typ", String.class);
        if (TYP_ADMIN.equals(typ)) {
            Long adminId = Long.valueOf(claims.getSubject());
            String email = claims.get("email", String.class);
            return generateAdmin(adminId, email != null ? email : "");
        }
        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        String role = claims.get("role", String.class);
        return generate(userId, username != null ? username : "", role != null ? role : "USER");
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

    /** Returns role claim if the token is valid, else null. */
    public String extractRole(String token) {
        try {
            return parse(token).get("role", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public String extractTyp(String token) {
        try {
            String typ = parse(token).get("typ", String.class);
            return typ != null ? typ : TYP_USER;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public String extractEmail(String token) {
        try {
            return parse(token).get("email", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isAdminToken(String token) {
        return TYP_ADMIN.equals(extractTyp(token));
    }
}
