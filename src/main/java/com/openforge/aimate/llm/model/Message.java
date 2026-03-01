package com.openforge.aimate.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 *
 * DeepSeek thinking mode: assistant messages in history must include
 * {@code reasoning_content} (see https://api-docs.deepseek.com/guides/thinking_mode#tool-calls).
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
        @JsonProperty("tool_calls") List<ToolCall> toolCalls,

        /**
         * Present only in tool-result messages.
         * Must match the id from the corresponding ToolCall.
         */
        @JsonProperty("tool_call_id") String toolCallId,

        /**
         * DeepSeek thinking mode: chain-of-thought for this assistant turn.
         * When using deepseek-reasoner (or thinking enabled), every assistant message
         * in the request must have this field (use "" if none).
         */
        @JsonProperty("reasoning_content") String reasoningContent
) {

    // ── Static factory helpers ──────────────────────────────────────────────

    public static Message system(String content) {
        return Message.builder().role("system").content(content).build();
    }

    public static Message user(String content) {
        return Message.builder().role("user").content(content).build();
    }

    public static Message assistantText(String content) {
        return Message.builder().role("assistant").content(content).reasoningContent("").build();
    }

    public static Message assistantToolCalls(List<ToolCall> toolCalls) {
        return Message.builder().role("assistant").toolCalls(toolCalls).reasoningContent("").build();
    }

    public static Message toolResult(String toolCallId, String result) {
        return Message.builder().role("tool").toolCallId(toolCallId).content(result).build();
    }
}
