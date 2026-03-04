package com.openforge.aimate.memory.dto;

import java.util.List;

/** Response for GET /api/memories/migration-status (camelCase for frontend). */
public record MigrationStatusResponse(
        String status,
        int    totalSessions,
        int    processedSessions,
        int    writtenMemories,
        String currentTask,
        String error,
        List<String> stepLog
) {
    public static MigrationStatusResponse idle() {
        return new MigrationStatusResponse("IDLE", 0, 0, 0, null, null, List.of());
    }
}
