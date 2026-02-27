package com.openforge.aimate.llm.model;

/**
 * The "function" sub-object inside a ToolCall returned by the LLM.
 *
 * "arguments" is a raw JSON string (not a parsed object) —
 * the caller must parse it with ObjectMapper as needed.
 *
 * Example:
 *   name      = "search_web"
 *   arguments = "{\"query\":\"Java 21 virtual threads\"}"
 */
public record FunctionCallResult(
        String name,
        String arguments
) {}
