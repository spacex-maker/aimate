package com.openforge.aimate.websocket;

import com.openforge.aimate.agent.event.AgentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin facade over SimpMessagingTemplate that routes AgentEvents to the
 * correct STOMP topic for each session.
 *
 * Topic layout:
 *   /topic/agent/{sessionId}  → all events for one session
 *
 * Usage in the Agent loop:
 *   publisher.publish(AgentEvent.thinking(sessionId, token, iteration));
 *
 * Thread safety: SimpMessagingTemplate is thread-safe; virtual threads can
 * call publish() concurrently without any additional synchronization.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentEventPublisher {

    private static final String TOPIC_PREFIX = "/topic/agent/";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Publish an event to all WebSocket subscribers of the given session.
     * Fire-and-forget — the caller is never blocked by slow consumers.
     */
    /**
     * 发送到 /topic/agent/{sessionId}，前端需 SUBSCRIBE 同一 destination 才能收到。
     * 若前端连接/订阅晚于 Agent 开始，会漏事件，需在 onConnected 时用 HTTP 拉会话+消息做兜底。
     */
    public void publish(AgentEvent event) {
        String destination = TOPIC_PREFIX + event.sessionId();
        try {
            messagingTemplate.convertAndSend(destination, event);
        } catch (Exception e) {
            log.warn("[Publisher] Failed to deliver {} event to {}: {}",
                    event.type(), destination, e.getMessage());
        }
    }

    /**
     * Convenience overload — publish with explicit sessionId (useful when
     * composing the event inline without a pre-built AgentEvent).
     */
    public void publish(String sessionId, AgentEvent event) {
        publish(event);
    }
}
