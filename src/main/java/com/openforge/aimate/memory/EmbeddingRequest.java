package com.openforge.aimate.memory;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for POST /v1/embeddings (OpenAI-compatible).
 *
 * Wire format:
 * {
 *   "input": "text to embed",
 *   "model": "text-embedding-3-small",
 *   "dimensions": 1536   // optional; only supported by text-embedding-3-*
 * }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmbeddingRequest(
        String input,
        String model,
        Integer dimensions
) {
    public static EmbeddingRequest of(String input, String model, int dimensions) {
        return new EmbeddingRequest(input, model, dimensions);
    }
}
