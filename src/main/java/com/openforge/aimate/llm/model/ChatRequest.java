package com.openforge.aimate.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.List;

/**
 * The request body sent to an OpenAI-compatible /chat/completions endpoint.
 *
 * toolChoice accepts:
 *   "none"     — model will not call any tool
 *   "auto"     — model decides (default)
 *   "required" — model MUST call at least one tool
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(
        String model,
        List<Message> messages,
        List<Tool> tools,
        String toolChoice,
        Double temperature,
        Integer maxTokens
) {

    public static ChatRequest simple(String model, List<Message> messages) {
        return ChatRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(0.7)
                .maxTokens(4096)
                .build();
    }

    public static ChatRequest withTools(String model, List<Message> messages, List<Tool> tools) {
        return ChatRequest.builder()
                .model(model)
                .messages(messages)
                .tools(tools)
                .toolChoice("auto")
                .temperature(0.7)
                .maxTokens(4096)
                .build();
    }
}
