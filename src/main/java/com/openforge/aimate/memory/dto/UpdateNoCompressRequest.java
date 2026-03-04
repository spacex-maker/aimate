package com.openforge.aimate.memory.dto;

/** Request body for PATCH /api/memories/{id}/no-compress (mark memory as protected from compression). */
public record UpdateNoCompressRequest(Boolean noCompress) {}
