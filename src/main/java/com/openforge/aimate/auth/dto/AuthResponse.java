package com.openforge.aimate.auth.dto;

/**
 * Simple auth response. Token is a placeholder for future JWT / session implementation.
 */
public record AuthResponse(
        Long userId,
        String username,
        String displayName,
        String token
) {
}

