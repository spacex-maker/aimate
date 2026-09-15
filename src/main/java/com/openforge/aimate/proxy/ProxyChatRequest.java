package com.openforge.aimate.proxy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Desktop / OpenAI-compatible proxy request body (camelCase).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProxyChatRequest(
        List<ProxyMessage> messages,
        List<ProxyTool> tools,
        String toolChoice,
        Double temperature,
        Integer maxTokens,
        String model,
        String sessionId
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProxyMessage(
            String role,
            String content,
            List<ProxyToolCall> toolCalls,
            String toolCallId,
            String reasoningContent
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProxyTool(
            String type,
            ProxyToolFunction function
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProxyToolFunction(
            String name,
            String description,
            JsonNode parameters
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProxyToolCall(
            String id,
            String type,
            ProxyFunctionCall function
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProxyFunctionCall(
            String name,
            String arguments
    ) {}
}
