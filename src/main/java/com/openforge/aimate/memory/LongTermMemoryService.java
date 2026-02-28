package com.openforge.aimate.memory;

import com.google.gson.JsonObject;
import com.openforge.aimate.embedding.UserEmbeddingResolver;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Long-term memory service — the Agent's persistent knowledge store.
 *
 * Three operation groups:
 *
 *   remember()   — embed + store (used by Agent loop automatically)
 *   recall()     — ANN search (used at session start for context injection)
 *   browse/manage— query by filter, delete, count (used by MemoryController REST API)
 */
@Slf4j
@Service
public class LongTermMemoryService {

    private static final float   MIN_SCORE_THRESHOLD = 0.75f;
    private static final List<String> ALL_FIELDS = List.of(
            "id", "user_id", "session_id", "content", "memory_type", "importance", "create_time_ms");

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** May be null when agent.milvus.enabled=false or Milvus is unreachable. */
    private final MilvusClientV2          milvusClient;
    private final EmbeddingClient         embeddingClient;   // system fallback
    private final MilvusProperties        milvusProps;
    private final UserEmbeddingResolver   embeddingResolver;
    private final MilvusCollectionManager collectionManager;
    private final HttpClient              httpClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    public LongTermMemoryService(@Nullable MilvusClientV2 milvusClient,
                                 EmbeddingClient embeddingClient,
                                 MilvusProperties milvusProps,
                                 UserEmbeddingResolver embeddingResolver,
                                 MilvusCollectionManager collectionManager,
                                 HttpClient httpClient,
                                 com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.milvusClient       = milvusClient;
        this.embeddingClient    = embeddingClient;
        this.milvusProps        = milvusProps;
        this.embeddingResolver  = embeddingResolver;
        this.collectionManager  = collectionManager;
        this.httpClient         = httpClient;
        this.objectMapper       = objectMapper;
        if (milvusClient == null) {
            log.warn("[Memory] MilvusClientV2 is not available — long-term memory features disabled.");
        }
    }

    /**
     * Resolves the effective EmbeddingClient + collection name for a user.
     * Falls back to the system client + default collection if no user config found.
     */
    private record EmbeddingContext(EmbeddingClient client, String collectionName) {}

    private EmbeddingContext resolveContext(@Nullable Long userId) {
        if (userId != null) {
            var resolved = embeddingResolver.resolveDefault(userId);
            if (resolved.isPresent()) {
                var r = resolved.get();
                collectionManager.ensureCollection(r.collectionName(), r.dimension());
                return new EmbeddingContext(
                        new EmbeddingClient(httpClient, objectMapper, r.props()),
                        r.collectionName()
                );
            }
        }
        return new EmbeddingContext(embeddingClient, milvusProps.collectionName());
    }

    private boolean milvusUnavailable() {
        if (milvusClient == null) {
            log.debug("[Memory] Skipped — Milvus not connected.");
            return true;
        }
        return false;
    }

    // ── Store ────────────────────────────────────────────────────────────────

    /** Store with user-configured embedding model (preferred). */
    public void remember(String sessionId,
                         String content,
                         MemoryType memoryType,
                         float importance,
                         @Nullable Long userId) {
        if (milvusUnavailable()) return;
        try {
            EmbeddingContext ctx = resolveContext(userId);
            List<Float> vector  = ctx.client().embed(content);

            JsonObject row = new JsonObject();
            if (userId != null) {
                row.addProperty("user_id", String.valueOf(userId));
            } else {
                row.addProperty("user_id", "");
            }
            row.addProperty("session_id",     sessionId);
            row.addProperty("content",        truncate(content, 4000));
            row.addProperty("memory_type",    memoryType.name());
            row.addProperty("importance",     importance);
            row.addProperty("create_time_ms", System.currentTimeMillis());

            com.google.gson.JsonArray embeddingArray = new com.google.gson.JsonArray();
            for (Float f : vector) embeddingArray.add(f);
            row.add("embedding", embeddingArray);

            milvusClient.insert(InsertReq.builder()
                    .collectionName(ctx.collectionName())
                    .data(List.of(row))
                    .build());

            log.debug("[Memory] Stored {} memory for session {} (importance={} collection={})",
                    memoryType, sessionId, importance, ctx.collectionName());
        } catch (Exception e) {
            log.warn("[Memory] Failed to store memory for session {}: {}", sessionId, e.getMessage());
        }
    }

    /** Backward-compatible overload — uses system embedding. */
    public void remember(String sessionId, String content, MemoryType memoryType, float importance) {
        remember(sessionId, content, memoryType, importance, null);
    }

    // ── Recall (ANN search) ──────────────────────────────────────────────────

    /** Recall with user-configured embedding model — across all memories for this user. */
    public List<MemoryRecord> recall(String queryText, int topK, @Nullable Long userId) {
        if (milvusUnavailable()) return List.of();
        try {
            EmbeddingContext ctx    = resolveContext(userId);
            List<Float>      vector = ctx.client().embed(queryText);
            // If we know the user, scope recall to their memories only.
            String filter = null;
            if (userId != null) {
                filter = "user_id == \"%s\"".formatted(userId);
            }
            return searchMilvus(vector, topK, filter, ctx.collectionName());
        } catch (Exception e) {
            log.warn("[Memory] Recall failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Backward-compatible overload — uses system embedding + default collection. */
    public List<MemoryRecord> recall(String queryText, int topK) {
        return recall(queryText, topK, null);
    }

    public List<MemoryRecord> recallFromSession(String queryText, String sessionId, int topK) {
        if (milvusUnavailable()) return List.of();
        try {
            List<Float> queryVector = embeddingClient.embed(queryText);
            return searchMilvus(queryVector, topK,
                    "session_id == \"%s\"".formatted(sessionId),
                    milvusProps.collectionName());
        } catch (Exception e) {
            log.warn("[Memory] Session recall failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Recall high-level semantic memories for a specific user, independent of session.
     * Used to build user-level profile in the Agent system prompt.
     */
    public List<MemoryRecord> recallUserSemantic(String queryText, int topK, Long userId) {
        if (milvusUnavailable() || userId == null) return List.of();
        try {
            EmbeddingContext ctx    = resolveContext(userId);
            List<Float>      vector = ctx.client().embed(queryText);
            String filter = "user_id == \"%s\" and memory_type == \"%s\""
                    .formatted(userId, MemoryType.SEMANTIC.name());
            return searchMilvus(vector, topK, filter, ctx.collectionName());
        } catch (Exception e) {
            log.warn("[Memory] User semantic recall failed: {}", e.getMessage());
            return List.of();
        }
    }

    public String formatForPrompt(List<MemoryRecord> memories) {
        if (memories.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("## Relevant memories from past experience:\n");
        for (MemoryRecord m : memories) {
            sb.append("- [%s] %s (relevance: %.2f)\n"
                    .formatted(m.memoryType().name(), m.content(), m.score()));
        }
        return sb.toString();
    }

    // ── Browse (scalar filter, no vector) ────────────────────────────────────

    /**
     * List memories with optional filters, supporting pagination.
     *
     * @param memoryType  null = all types
     * @param sessionId   null = all sessions
     * @param keyword     null = no keyword filter (exact substring in content)
     * @param offset      pagination offset
     * @param limit       page size (max 100)
     * @return page of MemoryItem
     */
    public List<MemoryItem> listMemories(MemoryType memoryType,
                                         String sessionId,
                                         String keyword,
                                         long offset,
                                         int limit) {
        if (milvusUnavailable()) return List.of();
        String filter = buildFilter(memoryType, sessionId, keyword);

        QueryReq.QueryReqBuilder builder = QueryReq.builder()
                .collectionName(milvusProps.collectionName())
                .outputFields(ALL_FIELDS)
                .offset(offset)
                .limit(Math.min(limit, 100));

        if (filter != null) builder.filter(filter);

        QueryResp resp = milvusClient.query(builder.build());
        return toMemoryItems(resp);
    }

    /**
     * Count total memories (with optional filter).
     * Uses a COUNT expression query — no data transfer.
     */
    public long countMemories(MemoryType memoryType, String sessionId) {
        if (milvusUnavailable()) return 0L;
        String filter = buildFilter(memoryType, sessionId, null);

        QueryReq.QueryReqBuilder builder = QueryReq.builder()
                .collectionName(milvusProps.collectionName())
                .outputFields(List.of("count(*)"));

        if (filter != null) builder.filter(filter);

        QueryResp resp = milvusClient.query(builder.build());
        if (resp == null || resp.getQueryResults().isEmpty()) return 0L;

        Object count = resp.getQueryResults().get(0).getEntity().get("count(*)");
        return count instanceof Number n ? n.longValue() : 0L;
    }

    /**
     * Semantic search visible to the user (no threshold applied — returns all results).
     */
    public List<MemoryItem> searchMemories(String queryText, int topK) {
        if (milvusUnavailable()) return List.of();
        List<Float> queryVector = embeddingClient.embed(queryText);

        SearchResp resp = milvusClient.search(SearchReq.builder()
                .collectionName(milvusProps.collectionName())
                .data(List.of(new FloatVec(queryVector)))
                .annsField("embedding")
                .topK(topK)
                .outputFields(ALL_FIELDS)
                .build());

        List<MemoryItem> results = new ArrayList<>();
        if (resp == null || resp.getSearchResults() == null) return results;

        for (List<SearchResp.SearchResult> row : resp.getSearchResults()) {
            for (SearchResp.SearchResult hit : row) {
                Float s = hit.getScore();
                results.add(toMemoryItem(hit.getId(), hit.getEntity(), s == null ? null : s.doubleValue()));
            }
        }
        return results;
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    /** Delete a single memory by its Milvus auto-generated id. */
    public void deleteById(long id) {
        if (milvusUnavailable()) return;
        milvusClient.delete(DeleteReq.builder()
                .collectionName(milvusProps.collectionName())
                .ids(List.of(id))
                .build());
        log.info("[Memory] Deleted memory id={}", id);
    }

    /** Delete all memories for a given session. */
    public long deleteBySession(String sessionId) {
        if (milvusUnavailable()) return 0L;
        milvusClient.delete(DeleteReq.builder()
                .collectionName(milvusProps.collectionName())
                .filter("session_id == \"%s\"".formatted(sessionId))
                .build());
        log.info("[Memory] Deleted memories for session {}", sessionId);
        return 0; // Milvus v2 delete doesn't return count; caller can query before if needed
    }

    /** Delete all memories of a given type. */
    public void deleteByType(MemoryType memoryType) {
        if (milvusUnavailable()) return;
        milvusClient.delete(DeleteReq.builder()
                .collectionName(milvusProps.collectionName())
                .filter("memory_type == \"%s\"".formatted(memoryType.name()))
                .build());
        log.info("[Memory] Deleted all {} memories", memoryType);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private List<MemoryRecord> searchMilvus(List<Float> queryVector, int topK,
                                            String filter, String collectionName) {
        SearchReq.SearchReqBuilder builder = SearchReq.builder()
                .collectionName(collectionName)
                .data(List.of(new FloatVec(queryVector)))
                .annsField("embedding")
                    .topK(topK)
                    .outputFields(List.of("content", "memory_type", "user_id", "session_id", "importance"));

        if (filter != null && !filter.isBlank()) builder.filter(filter);

        SearchResp resp = milvusClient.search(builder.build());

        List<MemoryRecord> results = new ArrayList<>();
        if (resp == null || resp.getSearchResults() == null) return results;

        for (List<SearchResp.SearchResult> row : resp.getSearchResults()) {
            for (SearchResp.SearchResult hit : row) {
                Float scoreF = hit.getScore();
                double score = scoreF == null ? 0.0 : scoreF.doubleValue();
                if (score < MIN_SCORE_THRESHOLD) continue;
                Map<String, Object> e = hit.getEntity();
                results.add(new MemoryRecord(
                        str(e, "content"), memType(e), str(e, "session_id"),
                        num(e, "importance"), score));
            }
        }
        return results;
    }

    private List<MemoryItem> toMemoryItems(QueryResp resp) {
        List<MemoryItem> list = new ArrayList<>();
        if (resp == null) return list;
        for (QueryResp.QueryResult r : resp.getQueryResults()) {
            Map<String, Object> e = r.getEntity();
            list.add(toMemoryItem(e.get("id"), e, null));
        }
        return list;
    }

    private MemoryItem toMemoryItem(Object rawId, Map<String, Object> entity, Double score) {
        long   id            = rawId instanceof Number n ? n.longValue() : 0L;
        long   createTimeMs  = ((Number) entity.getOrDefault("create_time_ms", 0L)).longValue();
        String createTimeStr = createTimeMs > 0
                ? TIME_FMT.format(Instant.ofEpochMilli(createTimeMs))
                : "-";

        return new MemoryItem(
                id,
                str(entity, "session_id"),
                str(entity, "content"),
                memType(entity),
                num(entity, "importance"),
                createTimeStr,
                score
        );
    }

    /**
     * Build a Milvus filter expression from optional parameters.
     * Combines multiple conditions with AND.
     */
    private String buildFilter(MemoryType memoryType, String sessionId, String keyword) {
        List<String> parts = new ArrayList<>();
        if (memoryType != null)
            parts.add("memory_type == \"%s\"".formatted(memoryType.name()));
        if (sessionId != null && !sessionId.isBlank())
            parts.add("session_id == \"%s\"".formatted(sessionId));
        if (keyword != null && !keyword.isBlank())
            parts.add("content like \"%%%s%%\"".formatted(keyword));

        return parts.isEmpty() ? null : String.join(" and ", parts);
    }

    // ── Field extractors ─────────────────────────────────────────────────────

    private String str(Map<String, Object> e, String key) {
        return (String) e.getOrDefault(key, "");
    }

    private float num(Map<String, Object> e, String key) {
        return ((Number) e.getOrDefault(key, 0f)).floatValue();
    }

    private MemoryType memType(Map<String, Object> e) {
        try { return MemoryType.valueOf(str(e, "memory_type")); }
        catch (Exception ex) { return MemoryType.EPISODIC; }
    }

    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }
}
