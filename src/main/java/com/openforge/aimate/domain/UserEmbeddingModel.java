package com.openforge.aimate.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * User-configured embedding model.
 *
 * DESIGN RULE: every (provider, modelName) combination lives in its own
 * Milvus collection (stored in collectionName). Vectors from different
 * models are never mixed — their spaces are incompatible.
 *
 * collectionName derivation:
 *   memories_{sanitized_model_name}_{dimension}
 *   e.g. text-embedding-3-small/1536 → memories_text_embedding_3_small_1536
 *        nomic-embed-text/768        → memories_nomic_embed_text_768
 */
@Getter
@Setter
@Entity
@Table(name = "user_embedding_models")
public class UserEmbeddingModel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Human-friendly label set by the user. */
    @Column(nullable = false, length = 128)
    private String name;

    /** openai | ollama | azure | custom */
    @Column(nullable = false, length = 32)
    private String provider;

    /** e.g. text-embedding-3-small, nomic-embed-text, mxbai-embed-large */
    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    /** Null for local Ollama deployments. */
    @Column(name = "api_key", length = 512)
    private String apiKey;

    /** https://api.openai.com/v1  OR  http://localhost:11434 */
    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    /** Vector dimension — determines which Milvus collection is used. */
    @Column(nullable = false)
    private int dimension;

    /** Auto-derived Milvus collection name: memories_{model_sanitized}_{dim} */
    @Column(name = "collection_name", nullable = false, length = 128)
    private String collectionName;

    @Column(name = "max_tokens", nullable = false)
    private int maxTokens = 8192;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    // ── Static helpers ────────────────────────────────────────────────────────

    /** Derive the Milvus collection name from model + dimension. */
    public static String deriveCollectionName(String modelName, int dimension) {
        String sanitized = modelName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        return "memories_" + sanitized + "_" + dimension;
    }
}
