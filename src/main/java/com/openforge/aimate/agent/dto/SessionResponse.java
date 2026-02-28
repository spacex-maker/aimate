package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openforge.aimate.domain.AgentSession;

import java.time.LocalDateTime;

/**
 * Response body for session management endpoints.
 *
 * Includes the WebSocket subscription path so the frontend can immediately
 * subscribe to the Agent's thought stream after creating a session.
 *
 * NOTE:
 *   We explicitly use camelCase JSON here (overriding any global SNAKE_CASE
 *   naming strategy) so that the frontend can rely on field names like
 *   "sessionId" instead of "session_id".
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SessionResponse(

        @JsonProperty("sessionId")      String        sessionId,
        @JsonProperty("status")         String        status,
        @JsonProperty("taskDescription")String        taskDescription,
        @JsonProperty("iterationCount") int           iterationCount,
        @JsonProperty("result")         String        result,
        @JsonProperty("errorMessage")   String        errorMessage,
        @JsonProperty("wsSubscribePath")String        wsSubscribePath,   // e.g. /topic/agent/{sessionId}
        @JsonProperty("createTime")     LocalDateTime createTime,
        @JsonProperty("updateTime")     LocalDateTime updateTime
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
