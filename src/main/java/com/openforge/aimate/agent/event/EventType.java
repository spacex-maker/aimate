package com.openforge.aimate.agent.event;

/**
 * Classifies every event the Agent emits over WebSocket.
 *
 * Flow: PLAN_READY → STEP_START(1) → STEP_COMPLETE(1) → STEP_START(2) → … → STEP_COMPLETE(n) → FINAL_ANSWER.
 *
 * Frontend: show plan steps; for current step show STEP_START then stream THINKING/TOOL_* then STEP_COMPLETE.
 */
public enum EventType {

    /** Execution plan (step titles) for this session. payload = List<String>. */
    PLAN_READY,

    /** A step is starting. payload = step index + title. */
    STEP_START,

    /** A step finished. payload = step index + title + optional summary. */
    STEP_COMPLETE,

    /** A single token from the streaming LLM (during step 2). */
    THINKING,

    /** The Agent is about to invoke a tool. payload = ToolCall. */
    TOOL_CALL,

    /** A tool has returned. payload = ToolResultPayload. */
    TOOL_RESULT,

    /** A new reasoning iteration inside "思考与执行". */
    ITERATION_START,

    /** @deprecated Use PLAN_READY. */
    PLAN_UPDATE,

    /** Final answer; session complete. */
    FINAL_ANSWER,

    /** Session status changed (e.g. RUNNING → PAUSED). */
    STATUS_CHANGE,

    /** Unrecoverable error. content = message. */
    ERROR
}
