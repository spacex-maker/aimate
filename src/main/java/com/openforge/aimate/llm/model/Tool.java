package com.openforge.aimate.llm.model;

/**
 * One tool entry in the "tools" array sent to the LLM.
 *
 * Wire format:
 * {
 *   "type": "function",
 *   "function": { "name": "...", "description": "...", "parameters": { ... } }
 * }
 */
public record Tool(
        String type,
        ToolFunction function
) {
    /** Convenience constructor — type is always "function" in current OpenAI spec. */
    public static Tool ofFunction(ToolFunction function) {
        return new Tool("function", function);
    }
}
