package com.openforge.aimate.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/agent/sessions.
 *
 * @param task          the natural-language task to give the Agent
 * @param sessionId     optional; if null the server generates a UUID
 */
public record StartSessionRequest(

        @NotBlank(message = "task must not be blank")
        @Size(max = 4000, message = "task must not exceed 4000 characters")
        String task,

        String sessionId
) {}
