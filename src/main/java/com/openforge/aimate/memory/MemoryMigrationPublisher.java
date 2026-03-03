package com.openforge.aimate.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket publisher for memory migration progress events.
 *
 * Topic: /topic/memory-migration/{userId}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryMigrationPublisher {

    private static final String TOPIC_PREFIX = "/topic/memory-migration/";

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(Long userId, MemoryMigrationEvent event) {
        if (userId == null) return;
        String destination = TOPIC_PREFIX + userId;
        try {
            messagingTemplate.convertAndSend(destination, event);
            if (log.isDebugEnabled()) {
                log.debug("[WS] Migration {} -> {}", event.type(), destination);
            }
        } catch (Exception e) {
            log.warn("[MigrationPublisher] Failed to deliver {} to {}: {}",
                    event.type(), destination, e.getMessage());
        }
    }
}

