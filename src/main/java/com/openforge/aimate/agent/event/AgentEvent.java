package com.openforge.aimate.agent.event;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The single event envelope broadcast over WebSocket.
 *
 * Every event the Agent emits — a thinking token, a tool call, a final
 * answer — is wrapped in this record before being pushed to the STOMP topic.
 *
 * Fields:
 *   sessionId  — the Agent session this event belongs to
 *   type       — discriminator; tells the frontend how to render the event
 *   content    — free-form text (token for THINKING, answer for FINAL_ANSWER,
 *                error message for ERROR)
 *   payload    — structured object for rich events (ToolCall, ToolResultPayload …)
 *                null for token-level events (THINKING, ITERATION_START)
 *   iteration  — which reasoning loop iteration produced this event
 *   timestamp  — epoch millis; useful for latency measurement on the client
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentEvent(
        String    sessionId,
        EventType type,
        String    content,
        Object    payload,
        int       iteration,
        long      timestamp
) {

    // ── Static factory helpers ───────────────────────────────────────────────

    public static AgentEvent thinking(String sessionId, String token, int iteration) {
        return new AgentEvent(sessionId, EventType.THINKING, token, null, iteration, now());
    }

    public static AgentEvent iterationStart(String sessionId, int iteration) {
        return new AgentEvent(sessionId, EventType.ITERATION_START, null, null, iteration, now());
    }

    public static AgentEvent toolCall(String sessionId, Object toolCallPayload, int iteration) {
        return new AgentEvent(sessionId, EventType.TOOL_CALL, null, toolCallPayload, iteration, now());
    }

    public static AgentEvent toolResult(String sessionId, String toolName, String result, int iteration) {
        return new AgentEvent(sessionId, EventType.TOOL_RESULT, result,
                new ToolResultPayload(toolName, result), iteration, now());
    }

    public static AgentEvent planUpdate(String sessionId, Object plan, int iteration) {
        return new AgentEvent(sessionId, EventType.PLAN_UPDATE, null, plan, iteration, now());
    }

    public static AgentEvent finalAnswer(String sessionId, String answer, int iteration) {
        return new AgentEvent(sessionId, EventType.FINAL_ANSWER, answer, null, iteration, now());
    }

    public static AgentEvent statusChange(String sessionId, String newStatus) {
        return new AgentEvent(sessionId, EventType.STATUS_CHANGE, newStatus, null, 0, now());
    }

    public static AgentEvent error(String sessionId, String message, int iteration) {
        return new AgentEvent(sessionId, EventType.ERROR, message, null, iteration, now());
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    // ── Nested payload types ─────────────────────────────────────────────────

    public record ToolResultPayload(String toolName, String output) {}
}
