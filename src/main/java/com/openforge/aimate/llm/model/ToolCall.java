package com.openforge.aimate.llm.model;

/**
 * A single tool invocation request produced by the LLM.
 *
 * The Agent loop must:
 *   1. Extract all ToolCalls from the assistant message.
 *   2. Execute each one (dispatch to Java bean, script, or shell).
 *   3. Append a Message.toolResult(id, output) for each ToolCall back
 *      into contextWindow so the LLM can continue reasoning.
 */
public record ToolCall(
        String id,
        String type,
        FunctionCallResult function
) {}
