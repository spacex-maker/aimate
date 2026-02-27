package com.openforge.aimate.embedding;

import com.openforge.aimate.domain.UserEmbeddingModel;
import com.openforge.aimate.memory.EmbeddingProperties;
import com.openforge.aimate.repository.UserEmbeddingModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves a user's default embedding model into an {@link EmbeddingProperties}
 * that can be directly used to construct a new {@link com.openforge.aimate.memory.EmbeddingClient}.
 *
 * If the user has no configuration, returns empty → caller falls back
 * to the system-level EmbeddingClient bean.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserEmbeddingResolver {

    private final UserEmbeddingModelRepository embRepo;

    public Optional<ResolvedEmbedding> resolveDefault(Long userId) {
        if (userId == null) return Optional.empty();

        return embRepo.findByUserIdAndIsDefaultTrueAndIsActiveTrue(userId)
                .map(m -> {
                    log.debug("[EmbeddingResolver] userId={} → model={} dim={} collection={}",
                            userId, m.getModelName(), m.getDimension(), m.getCollectionName());
                    return new ResolvedEmbedding(
                            toProps(m),
                            m.getCollectionName(),
                            m.getDimension()
                    );
                });
    }

    // ── Resolved result ───────────────────────────────────────────────────────

    /**
     * Carries both the EmbeddingProperties (for building a client) and the
     * Milvus collection name + dimension (for routing storage/search).
     */
    public record ResolvedEmbedding(
            EmbeddingProperties props,
            String              collectionName,
            int                 dimension
    ) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static EmbeddingProperties toProps(UserEmbeddingModel m) {
        // Ollama uses a dummy "bearer" token if apiKey is blank
        String apiKey = (m.getApiKey() != null && !m.getApiKey().isBlank())
                ? m.getApiKey()
                : "ollama";
        return new EmbeddingProperties(
                m.getBaseUrl(),
                apiKey,
                m.getModelName(),
                m.getDimension(),
                30
        );
    }
}
