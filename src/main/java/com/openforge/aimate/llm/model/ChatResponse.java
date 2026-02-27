package com.openforge.aimate.llm.model;

import java.util.List;

/**
 * Top-level response from /chat/completions.
 */
public record ChatResponse(
        String id,
        String object,
        Long created,
        String model,
        List<Choice> choices,
        Usage usage
) {

    /** Convenience: first choice message (always present for non-streaming responses). */
    public Message firstMessage() {
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("LLM returned no choices in response: " + id);
        }
        return choices.get(0).message();
    }

    /** True if the model wants to call one or more tools. */
    public boolean hasToolCalls() {
        if (choices == null || choices.isEmpty()) return false;
        Message msg = choices.get(0).message();
        return msg != null && msg.toolCalls() != null && !msg.toolCalls().isEmpty();
    }

    public record Choice(
            int index,
            Message message,
            String finishReason
    ) {}

    public record Usage(
            int promptTokens,
            int completionTokens,
            int totalTokens
    ) {}
}
