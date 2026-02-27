package com.openforge.aimate.memory;

import java.util.List;

/**
 * Response from POST /v1/embeddings.
 *
 * Wire format:
 * {
 *   "object": "list",
 *   "data": [
 *     { "object": "embedding", "index": 0, "embedding": [0.1, -0.2, ...] }
 *   ],
 *   "model": "text-embedding-3-small",
 *   "usage": { "prompt_tokens": 8, "total_tokens": 8 }
 * }
 */
public record EmbeddingResponse(
        String object,
        List<EmbeddingData> data,
        String model,
        Usage usage
) {

    /** Extract the embedding vector from the first (and typically only) result. */
    public List<Float> firstEmbedding() {
        if (data == null || data.isEmpty()) {
            throw new IllegalStateException("Embedding response contained no data");
        }
        return data.get(0).embedding();
    }

    public record EmbeddingData(
            String object,
            int index,
            List<Float> embedding
    ) {}

    public record Usage(
            int promptTokens,
            int totalTokens
    ) {}
}
