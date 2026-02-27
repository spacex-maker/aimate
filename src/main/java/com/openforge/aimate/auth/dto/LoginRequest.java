package com.openforge.aimate.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login using username or email + password.
 */
public record LoginRequest(
        @NotBlank
        @Size(max = 128)
        String identifier,

        @NotBlank
        @Size(min = 6, max = 72)
        String password
) {
}

