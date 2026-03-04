package com.openforge.aimate.memory.dto;

import com.openforge.aimate.memory.MemoryType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for POST /api/memories. */
public record AddMemoryRequest(
        @NotBlank @Size(max = 4000) String content,
        MemoryType memoryType,
        String     sessionId,
        Float      importance
) {}
