package com.openforge.aimate.auth.dto;

/**
 * Auth response returned after login/register.
 */
public record AuthResponse(
        Long   userId,
        String username,
        String displayName,
        String role,
        String token
) {}

