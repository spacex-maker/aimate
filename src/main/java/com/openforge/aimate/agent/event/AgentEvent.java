package com.openforge.aimate.agent.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

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
 *   eventId    — unique id per emission; frontend uses it to dedupe when same event is delivered twice (e.g. double subscription)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentEvent(
        String    sessionId,
        EventType type,
        String    content,
        Object    payload,
        int       iteration,
        long      timestamp,
        String    eventId
) {

    private static String nextEventId() {
        return UUID.randomUUID().toString();
    }

    // ── Static factory helpers ───────────────────────────────────────────────

    public static AgentEvent thinking(String sessionId, String token, int iteration) {
        return new AgentEvent(sessionId, EventType.THINKING, token, null, iteration, now(), nextEventId());
    }

    public static AgentEvent iterationStart(String sessionId, int iteration) {
        return new AgentEvent(sessionId, EventType.ITERATION_START, null, null, iteration, now(), nextEventId());
    }

    public static AgentEvent toolCall(String sessionId, Object toolCallPayload, int iteration) {
        return new AgentEvent(sessionId, EventType.TOOL_CALL, null, toolCallPayload, iteration, now(), nextEventId());
    }

    public static AgentEvent toolResult(String sessionId, String toolName, String result, int iteration) {
        return new AgentEvent(sessionId, EventType.TOOL_RESULT, result,
                new ToolResultPayload(toolName, result), iteration, now(), nextEventId());
    }

    /** 长耗时工具（如 run_container_cmd）执行过程中推送的实时输出片段；payload = ToolOutputChunkPayload */
    public static AgentEvent toolOutputChunk(String sessionId, String toolCallId, String chunk, int iteration) {
        return new AgentEvent(sessionId, EventType.TOOL_OUTPUT_CHUNK, null,
                new ToolOutputChunkPayload(toolCallId, chunk), iteration, now(), nextEventId());
    }

    public static AgentEvent planUpdate(String sessionId, Object plan, int iteration) {
        return new AgentEvent(sessionId, EventType.PLAN_UPDATE, null, plan, iteration, now(), nextEventId());
    }

    /** Plan steps (e.g. ["回忆", "思考与执行", "回答"]). payload = List<String>. */
    public static AgentEvent planReady(String sessionId, java.util.List<String> steps) {
        return new AgentEvent(sessionId, EventType.PLAN_READY, null, steps, 0, now(), nextEventId());
    }

    public static AgentEvent stepStart(String sessionId, int stepIndex, String title) {
        return new AgentEvent(sessionId, EventType.STEP_START, null,
                new StepPayload(stepIndex, title, null), 0, now(), nextEventId());
    }

    public static AgentEvent stepComplete(String sessionId, int stepIndex, String title, String summary) {
        return new AgentEvent(sessionId, EventType.STEP_COMPLETE, summary,
                new StepPayload(stepIndex, title, summary), 0, now(), nextEventId());
    }

    public static AgentEvent finalAnswer(String sessionId, String answer, int iteration) {
        return new AgentEvent(sessionId, EventType.FINAL_ANSWER, answer, null, iteration, now(), nextEventId());
    }

    /** 携带新 assistant 消息 id，前端可直接追加到消息列表、无需整表 refetch。payload = { "assistantMessageId": long } */
    public static AgentEvent finalAnswer(String sessionId, String answer, int iteration, long assistantMessageId) {
        return new AgentEvent(sessionId, EventType.FINAL_ANSWER, answer,
                java.util.Map.of("assistantMessageId", assistantMessageId), iteration, now(), nextEventId());
    }

    public static AgentEvent statusChange(String sessionId, String newStatus) {
        return new AgentEvent(sessionId, EventType.STATUS_CHANGE, newStatus, null, 0, now(), nextEventId());
    }

    /** 携带会话快照，前端可直接更新缓存、无需再轮询。payload 为 SessionResponse 或等效 DTO。 */
    public static AgentEvent statusChange(String sessionId, String newStatus, Object sessionSnapshot) {
        return new AgentEvent(sessionId, EventType.STATUS_CHANGE, newStatus, sessionSnapshot, 0, now(), nextEventId());
    }

    public static AgentEvent error(String sessionId, String message, int iteration) {
        return new AgentEvent(sessionId, EventType.ERROR, message, null, iteration, now(), nextEventId());
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    // ── Nested payload types ─────────────────────────────────────────────────

    public record ToolResultPayload(String toolName, String output) {}

    /** TOOL_OUTPUT_CHUNK 的 payload：当前工具调用 id 与本次输出的片段 */
    public record ToolOutputChunkPayload(String toolCallId, String chunk) {}

    /** step_index (1-based), title, optional summary. */
    public record StepPayload(int stepIndex, String title, String summary) {}
}
