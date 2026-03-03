package com.openforge.aimate.memory;

/**
 * DTO sent over WebSocket to report migration progress.
 */
public record MemoryMigrationEvent(
        String type,              // START | PROGRESS | DONE | ERROR
        long   timestamp,
        int    totalSessions,
        int    processedSessions,
        int    writtenMemories,
        String currentSessionId,
        String currentTaskDescription,
        String error
) {
    public static MemoryMigrationEvent start(long now) {
        return new MemoryMigrationEvent("START", now, 0, 0, 0, null, null, null);
    }

    public static MemoryMigrationEvent progress(long now,
                                                int totalSessions,
                                                int processedSessions,
                                                int writtenMemories,
                                                String currentSessionId,
                                                String currentTaskDescription) {
        return new MemoryMigrationEvent("PROGRESS", now, totalSessions, processedSessions,
                writtenMemories, currentSessionId, currentTaskDescription, null);
    }

    public static MemoryMigrationEvent done(long now, int totalSessions, int writtenMemories) {
        return new MemoryMigrationEvent("DONE", now, totalSessions, totalSessions,
                writtenMemories, null, null, null);
    }

    public static MemoryMigrationEvent error(long now, String error) {
        return new MemoryMigrationEvent("ERROR", now, 0, 0, 0, null, null, error);
    }
}

