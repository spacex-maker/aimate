package com.openforge.aimate.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 64)
        String username,

        @Email
        String email,

        @NotBlank
        @Size(min = 6, max = 72)
        String password,

        @Size(max = 128)
        String displayName
) {
}

