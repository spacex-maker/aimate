package com.openforge.aimate.memory.dto;

import com.openforge.aimate.memory.MemoryItem;

import java.util.List;

/** Paginated list response for GET /api/memories. */
public record MemoryPage(
        List<MemoryItem> items,
        long             total,
        int              page,
        int              size
) {}
