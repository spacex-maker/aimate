package com.openforge.aimate.agent.event;

/**
 * Classifies every event the Agent emits over WebSocket.
 *
 * Frontend rendering hints:
 *   THINKING       → append token to the "thinking bubble"
 *   TOOL_CALL      → show a spinning "Using tool: xxx" card
 *   TOOL_RESULT    → collapse tool card, show result snippet
 *   ITERATION_START→ open a new reasoning block in the UI
 *   PLAN_UPDATE    → render/refresh the step-list sidebar
 *   FINAL_ANSWER   → display the answer panel, mark session done
 *   STATUS_CHANGE  → update session status badge
 *   ERROR          → show error toast
 */
public enum EventType {

    /** A single token (or small chunk) from the streaming LLM response. */
    THINKING,

    /** The Agent is about to invoke a tool. payload = ToolCall. */
    TOOL_CALL,

    /** A tool has returned its output. payload = ToolResultPayload. */
    TOOL_RESULT,

    /** A new Agent reasoning iteration has started. */
    ITERATION_START,

    /** The planner produced or updated a plan. payload = List<PlanStep>. */
    PLAN_UPDATE,

    /** The Agent has produced a final answer and the session is complete. */
    FINAL_ANSWER,

    /** The session status has changed (e.g. RUNNING → PAUSED). */
    STATUS_CHANGE,

    /** An unrecoverable error occurred. content = error message. */
    ERROR
}
