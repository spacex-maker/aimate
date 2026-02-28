package com.openforge.aimate.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for continuing an existing Agent session with a new
 * user message (multi-turn conversation).
 */
public record ContinueSessionRequest(

        @NotBlank
        String message
) {
}

