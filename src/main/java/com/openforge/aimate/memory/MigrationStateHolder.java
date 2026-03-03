package com.openforge.aimate.memory;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory state for memory migration per user.
 * Allows the frontend to restore progress after refresh and to request cancel.
 */
@Component
public class MigrationStateHolder {

    private static final int MAX_STEP_LOG = 200;

    private final ConcurrentHashMap<Long, Snapshot> state = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicBoolean> cancelRequested = new ConcurrentHashMap<>();

    public void setRunning(Long userId) {
        if (userId == null) return;
        state.put(userId, new Snapshot("RUNNING", 0, 0, 0, null, null, new ArrayList<>()));
        cancelRequested.put(userId, new AtomicBoolean(false));
    }

    public void updateProgress(Long userId, int totalSessions, int processedSessions, int writtenMemories,
                               String currentTask, String stepDetail) {
        if (userId == null) return;
        state.compute(userId, (k, prev) -> {
            List<String> log = prev != null ? new ArrayList<>(prev.stepLog()) : new ArrayList<>();
            if (stepDetail != null && !stepDetail.isBlank()) {
                log.add(stepDetail);
                if (log.size() > MAX_STEP_LOG) log.remove(0);
            }
            return new Snapshot("RUNNING", totalSessions, processedSessions, writtenMemories,
                    currentTask, null, log);
        });
    }

    public void setDone(Long userId, int totalSessions, int writtenMemories) {
        if (userId == null) return;
        state.compute(userId, (k, prev) -> {
            List<String> log = prev != null ? new ArrayList<>(prev.stepLog()) : new ArrayList<>();
            log.add("同步完成");
            if (log.size() > MAX_STEP_LOG) log.remove(0);
            return new Snapshot("DONE", totalSessions, totalSessions, writtenMemories, null, null, log);
        });
    }

    public void setError(Long userId, String error) {
        if (userId == null) return;
        state.compute(userId, (k, prev) -> {
            List<String> log = prev != null ? new ArrayList<>(prev.stepLog()) : new ArrayList<>();
            log.add("错误: " + (error != null ? error : "未知"));
            return new Snapshot("ERROR", 0, 0, 0, null, error, log);
        });
    }

    public void setCancelled(Long userId, int totalSessions, int processedSessions, int writtenMemories) {
        if (userId == null) return;
        state.compute(userId, (k, prev) -> {
            List<String> log = prev != null ? new ArrayList<>(prev.stepLog()) : new ArrayList<>();
            log.add("已中断同步");
            return new Snapshot("CANCELLED", totalSessions, processedSessions, writtenMemories, null, null, log);
        });
    }

    public Snapshot get(Long userId) {
        if (userId == null) return null;
        return state.get(userId);
    }

    public void requestCancel(Long userId) {
        if (userId == null) return;
        cancelRequested.computeIfAbsent(userId, k -> new AtomicBoolean(false)).set(true);
    }

    public boolean isCancelRequested(Long userId) {
        if (userId == null) return false;
        AtomicBoolean b = cancelRequested.get(userId);
        return b != null && b.get();
    }

    public void clearCancel(Long userId) {
        if (userId == null) return;
        AtomicBoolean b = cancelRequested.get(userId);
        if (b != null) b.set(false);
    }

    public record Snapshot(
            String status,
            int totalSessions,
            int processedSessions,
            int writtenMemories,
            String currentTask,
            String error,
            List<String> stepLog
    ) {}
}
