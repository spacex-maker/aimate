package com.openforge.aimate.agent.dto;

import com.openforge.aimate.domain.AgentSession;

import java.time.LocalDateTime;

/**
 * Response body for session management endpoints.
 *
 * Includes the WebSocket subscription path so the frontend can immediately
 * subscribe to the Agent's thought stream after creating a session.
 */
public record SessionResponse(

        String        sessionId,
        String        status,
        String        taskDescription,
        int           iterationCount,
        String        result,
        String        errorMessage,
        String        wsSubscribePath,   // e.g. /topic/agent/{sessionId}
        LocalDateTime createTime,
        LocalDateTime updateTime
) {

    public static SessionResponse from(AgentSession session) {
        return new SessionResponse(
                session.getSessionId(),
                session.getStatus().name(),
                session.getTaskDescription(),
                session.getIterationCount(),
                session.getResult(),
                session.getErrorMessage(),
                "/topic/agent/" + session.getSessionId(),
                session.getCreateTime(),
                session.getUpdateTime()
        );
    }
}
