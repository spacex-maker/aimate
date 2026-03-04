package com.openforge.aimate.memory.dto;

import com.openforge.aimate.memory.MemoryType;

/** Response for GET /api/memories/count. */
public record CountResponse(
        long        count,
        MemoryType  type,
        String      session
) {}
