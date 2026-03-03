package com.openforge.aimate.memory;

/**
 * DTO sent over WebSocket to report migration progress.
 * stepDetail: human-readable step for UI log (e.g. "向量化用户消息 (1019 字) → 已写入 EPISODIC").
 */
public record MemoryMigrationEvent(
        String type,              // START | PROGRESS | DONE | ERROR
        long   timestamp,
        int    totalSessions,
        int    processedSessions,
        int    writtenMemories,
        String currentSessionId,
        String currentTaskDescription,
        String error,
        String stepDetail        // optional step description for detailed log
) {
    public static MemoryMigrationEvent start(long now) {
        return new MemoryMigrationEvent("START", now, 0, 0, 0, null, null, null, null);
    }

    public static MemoryMigrationEvent progress(long now,
                                                int totalSessions,
                                                int processedSessions,
                                                int writtenMemories,
                                                String currentSessionId,
                                                String currentTaskDescription,
                                                String stepDetail) {
        return new MemoryMigrationEvent("PROGRESS", now, totalSessions, processedSessions,
                writtenMemories, currentSessionId, currentTaskDescription, null, stepDetail);
    }

    public static MemoryMigrationEvent done(long now, int totalSessions, int writtenMemories) {
        return new MemoryMigrationEvent("DONE", now, totalSessions, totalSessions,
                writtenMemories, null, null, null, null);
    }

    public static MemoryMigrationEvent error(long now, String error) {
        return new MemoryMigrationEvent("ERROR", now, 0, 0, 0, null, null, error, null);
    }

    public static MemoryMigrationEvent cancelled(long now, int totalSessions, int processedSessions, int writtenMemories) {
        return new MemoryMigrationEvent("CANCELLED", now, totalSessions, processedSessions,
                writtenMemories, null, null, null, null);
    }
}

