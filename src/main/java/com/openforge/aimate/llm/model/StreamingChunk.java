package com.openforge.aimate.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Represents one SSE data frame from a streaming /chat/completions response.
 *
 * Wire format (one line from the SSE stream):
 *   data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk",
 *           "choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}
 *
 * Last frame:
 *   data: [DONE]
 *
 * Parsing logic in LlmClient.streamChat():
 *   1. Skip empty lines and lines not starting with "data: "
 *   2. If line == "data: [DONE]" → stream ended
 *   3. Otherwise strip "data: " prefix and deserialize as StreamingChunk
 *   4. Extract delta.content (may be null for role-only first frame)
 *   5. Extract delta.toolCalls for function-call streaming
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StreamingChunk(
        String id,
        String object,
        Long created,
        String model,
        List<ChunkChoice> choices
) {

    public record ChunkChoice(
            int index,
            DeltaMessage delta,
            String finishReason
    ) {
        /** True when this is the frame that signals end-of-generation. */
        public boolean isDone() {
            return "stop".equals(finishReason) || "tool_calls".equals(finishReason);
        }
    }

    /**
     * Sparse message delta — only the fields that changed in this chunk
     * are non-null.  The first chunk usually carries {"role":"assistant"},
     * subsequent chunks carry {"content":"token"} or {"tool_calls":[...]}.
     */
    public record DeltaMessage(
            String role,
            String content,
            List<ToolCallDelta> toolCalls
    ) {}

    /**
     * Incremental tool-call fragment.  The LLM streams the tool call name
     * in the first chunk and the arguments JSON spread across many chunks.
     * The caller must accumulate these into a complete ToolCall.
     */
    public record ToolCallDelta(
            Integer index,
            String id,
            String type,
            FunctionDelta function
    ) {}

    public record FunctionDelta(
            String name,
            String arguments   // partial JSON string; accumulate until finish_reason=tool_calls
    ) {}
}
