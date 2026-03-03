package com.openforge.aimate.memory;

import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.domain.SessionMessage;
import com.openforge.aimate.repository.AgentSessionRepository;
import com.openforge.aimate.repository.SessionMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * One-off migration: re-embed existing conversation history into
 * the user's CURRENT default embedding model / collection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryMigrationService {

    private final AgentSessionRepository   sessionRepository;
    private final SessionMessageRepository messageRepository;
    private final LongTermMemoryService    memoryService;
    private final MemoryMigrationPublisher migrationPublisher;

    /**
     * Start migration in a background virtual thread and stream progress
     * to /topic/memory-migration/{userId} via WebSocket.
     */
    public void startMigration(Long userId) {
        if (userId == null) return;
        Thread.startVirtualThread(() -> migrateUserConversationsWithEvents(userId));
    }

    /**
     * Re-embed all existing sessions/messages for the given user into the
     * currently selected embedding collection (resolveContext(userId)),
     * publishing START / PROGRESS / DONE / ERROR events along the way.
     */
    @Transactional(readOnly = true)
    void migrateUserConversationsWithEvents(Long userId) {
        long now = Instant.now().toEpochMilli();
        migrationPublisher.publish(userId, MemoryMigrationEvent.start(now));

        try {
            List<AgentSession> sessions = sessionRepository.findByUserId(userId);
            if (sessions.isEmpty()) {
                log.info("[MemoryMigration] No sessions found for userId={}", userId);
                migrationPublisher.publish(userId, MemoryMigrationEvent.done(now, 0, 0));
                return;
            }

            int totalSessions = sessions.size();
            int processedSessions = 0;
            int written = 0;

            for (AgentSession s : sessions) {
                List<SessionMessage> msgs =
                        messageRepository.findByAgentSession_IdOrderBySeqAsc(s.getId());
                if (!msgs.isEmpty()) {
                    String sessionId = s.getSessionId();
                    String taskDesc = s.getTaskDescription();
                    for (SessionMessage m : msgs) {
                        String role = m.getRole();
                        String content = m.getContent();
                        if (content == null || content.isBlank()) continue;

                        // 用户消息 → 情节记忆（EPISODIC）
                        if ("user".equals(role)) {
                            memoryService.remember(
                                    sessionId,
                                    "用户: " + content,
                                    MemoryType.EPISODIC,
                                    0.6f,
                                    userId
                            );
                            written++;
                        }
                        // 助手主消息（不包含工具附属消息）→ 语义记忆（SEMANTIC）
                        else if ("assistant".equals(role) && m.getReplyToMessageId() == null) {
                            memoryService.remember(
                                    sessionId,
                                    "助手回复: " + content,
                                    MemoryType.SEMANTIC,
                                    0.8f,
                                    userId
                            );
                            written++;
                        }
                    }
                    processedSessions++;
                    migrationPublisher.publish(
                            userId,
                            MemoryMigrationEvent.progress(
                                    Instant.now().toEpochMilli(),
                                    totalSessions,
                                    processedSessions,
                                    written,
                                    sessionId,
                                    taskDesc
                            )
                    );
                }
            }

            log.info("[MemoryMigration] Migrated {} memories for userId={}", written, userId);
            migrationPublisher.publish(userId, MemoryMigrationEvent.done(Instant.now().toEpochMilli(), totalSessions, written));
        } catch (Exception e) {
            log.warn("[MemoryMigration] Migration failed for userId={}: {}", userId, e.getMessage(), e);
            migrationPublisher.publish(userId, MemoryMigrationEvent.error(Instant.now().toEpochMilli(), e.getMessage()));
        }
    }
}

