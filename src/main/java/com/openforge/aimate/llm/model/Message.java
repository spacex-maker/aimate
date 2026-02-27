package com.openforge.aimate.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.List;

/**
 * A single entry in the LLM conversation history.
 *
 * role variants:
 *   "system"    — initial persona / instructions
 *   "user"      — human turn
 *   "assistant" — model reply; may contain tool_calls instead of content
 *   "tool"      — result returned after executing a tool call
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Message(
        String role,

        /** Text content. Null for assistant messages that only contain tool_calls. */
        String content,

        /**
         * Present only in assistant messages when the model wants to call one or
         * more tools.  Maps to the "tool_calls" array in the OpenAI wire format.
         */
        List<ToolCall> toolCalls,

        /**
         * Present only in tool-result messages.
         * Must match the id from the corresponding ToolCall.
         */
        String toolCallId
) {

    // ── Static factory helpers ──────────────────────────────────────────────

    public static Message system(String content) {
        return Message.builder().role("system").content(content).build();
    }

    public static Message user(String content) {
        return Message.builder().role("user").content(content).build();
    }

    public static Message assistantText(String content) {
        return Message.builder().role("assistant").content(content).build();
    }

    public static Message assistantToolCalls(List<ToolCall> toolCalls) {
        return Message.builder().role("assistant").toolCalls(toolCalls).build();
    }

    public static Message toolResult(String toolCallId, String result) {
        return Message.builder().role("tool").toolCallId(toolCallId).content(result).build();
    }
}
