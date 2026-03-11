package com.openforge.aimate.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

/**
 * The request body sent to an OpenAI-compatible /chat/completions endpoint.
 *
 * toolChoice accepts:
 *   "none"     — model will not call any tool
 *   "auto"     — model decides (default)
 *   "required" — model MUST call at least one tool
 *
 * 字段名严格按 OpenAI 官方 snake_case：
 * - tools        → "tools"
 * - toolChoice   → "tool_choice"
 * - maxTokens    → "max_tokens"
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(
        String model,
        List<Message> messages,
        List<Tool> tools,
        @JsonProperty("tool_choice") String toolChoice,
        Double temperature,
        @JsonProperty("max_tokens") Integer maxTokens
) {

    public static ChatRequest simple(String model, List<Message> messages) {
        return ChatRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(0.7)
                .maxTokens(4096)
                .build();
    }

    /** 带工具调用时提高 maxTokens，避免长命令（如大段 heredoc 脚本）在输出时被截断 */
    public static ChatRequest withTools(String model, List<Message> messages, List<Tool> tools) {
        return ChatRequest.builder()
                .model(model)
                .messages(messages)
                .tools(tools)
                .toolChoice("auto")
                .temperature(0.7)
                .maxTokens(16384)
                .build();
    }

    /** Force text-only reply (no tool calls). Use after duplicate store_memory skip to break loop. */
    public static ChatRequest withNoTools(String model, List<Message> messages) {
        return ChatRequest.builder()
                .model(model)
                .messages(messages)
                .tools(null)
                .toolChoice("none")
                .temperature(0.7)
                .maxTokens(4096)
                .build();
    }
}
