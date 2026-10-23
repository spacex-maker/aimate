package com.openforge.aimate.auth.dto;

/**
 * Auth response returned after login/register.
 */
public record AuthResponse(
        Long   userId,
        String username,
        String displayName,
        String avatarUrl,
        String role,
        String token
) {
    /** 兼容旧调用（无头像）。 */
    public AuthResponse(Long userId, String username, String displayName, String role, String token) {
        this(userId, username, displayName, null, role, token);
    }
}
