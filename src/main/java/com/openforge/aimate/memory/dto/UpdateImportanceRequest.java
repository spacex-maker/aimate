package com.openforge.aimate.memory.dto;

/** Request body for PATCH /api/memories/{id} (set importance 0.0–1.0). */
public record UpdateImportanceRequest(Float importance) {}
