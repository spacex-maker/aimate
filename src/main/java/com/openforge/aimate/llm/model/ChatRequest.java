package com.openforge.aimate.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The request body sent to an OpenAI-compatible /chat/completions endpoint.
 *
 * toolChoice accepts (OpenAI-compatible):
 *   "none"     — model will not call any tool
 *   "auto"     — model decides (default)
 *   "required" — model MUST call at least one tool
 *   {"type":"function","function":{"name":"..."}} — force a specific tool
 *
 * 字段名严格按 OpenAI 官方 snake_case。
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(
        String model,
        List<Message> messages,
        List<Tool> tools,
        @JsonProperty("tool_choice") Object toolChoice,
        Double temperature,
        @JsonProperty("max_tokens") Integer maxTokens,
        /**
         * DeepSeek 等：关闭思考链，避免 reasoning 吃光 max_tokens 导致 content 为空。
         * 例：{@code Thinking.disabled()}
         */
        Thinking thinking
) {
    /** OpenAI 兼容扩展：thinking.type = disabled | enabled */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Thinking(String type) {
        public static Thinking disabled() {
            return new Thinking("disabled");
        }
    }

    /** Force the model to call one named function tool. */
    public static Object forceFunctionTool(String functionName) {
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", functionName);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("type", "function");
        choice.put("function", fn);
        return choice;
    }

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
        return withTools(model, messages, tools, "auto");
    }

    public static ChatRequest withTools(String model, List<Message> messages, List<Tool> tools, Object toolChoice) {
        return ChatRequest.builder()
                .model(model)
                .messages(messages)
                .tools(tools)
                .toolChoice(toolChoice)
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
