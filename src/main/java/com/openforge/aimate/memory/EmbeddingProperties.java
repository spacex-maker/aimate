package com.openforge.aimate.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the OpenAI-compatible text embedding endpoint.
 *
 * application.yml:
 *
 * agent:
 *   embedding:
 *     base-url: https://api.openai.com/v1
 *     api-key: ${EMBEDDING_API_KEY:${PRIMARY_LLM_API_KEY:sk-placeholder}}
 *     model: text-embedding-3-small
 *     dimensions: 1536
 *     timeout-seconds: 30
 *
 * Dimension reference:
 *   text-embedding-3-small  → 1536  (cost-effective, good quality)
 *   text-embedding-3-large  → 3072  (best quality, higher cost)
 *   text-embedding-ada-002  → 1536  (legacy, still widely used)
 */
@ConfigurationProperties(prefix = "agent.embedding")
public record EmbeddingProperties(
        String baseUrl,
        String apiKey,
        @DefaultValue("text-embedding-3-small") String model,
        @DefaultValue("1536") int dimensions,
        @DefaultValue("30") int timeoutSeconds
) {}
