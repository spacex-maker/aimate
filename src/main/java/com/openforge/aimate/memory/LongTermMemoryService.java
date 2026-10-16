package com.openforge.aimate.memory;

import com.tencent.tcvectordb.model.DocField;
import com.tencent.tcvectordb.model.Document;
import com.tencent.tcvectordb.model.param.dml.HNSWSearchParams;
import com.tencent.tcvectordb.model.param.dml.CountQueryParam;
import com.tencent.tcvectordb.model.param.dml.DeleteParam;
import com.tencent.tcvectordb.model.param.dml.Filter;
import com.tencent.tcvectordb.model.param.dml.InsertParam;
import com.tencent.tcvectordb.model.param.dml.QueryParam;
import com.tencent.tcvectordb.model.param.dml.SearchByEmbeddingItemsParam;
import com.tencent.tcvectordb.model.param.dml.UpdateParam;
import com.tencent.tcvectordb.model.param.entity.SearchRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 长期记忆。文本写入腾讯云向量库，由库内向量模型生成向量并检索。
 */
@Slf4j
@Service
public class LongTermMemoryService {

    private static final float MIN_SCORE_THRESHOLD = 0.0f;
    private static final List<String> LIST_FIELDS = List.of(
            "id", "user_id", "session_id", "text", "memory_type", "importance", "no_compress", "create_time_ms");

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final TencentVectorStore store;

    public LongTermMemoryService(TencentVectorStore store) {
        this.store = store;
    }

    public String resolveCollectionName(@Nullable Long userId) {
        return store.memoryCollection();
    }

    /**
     * 清空当前用户在共享集合中的记忆。集合由全体用户共用，不能整表删除。
     */
    public boolean recreateCollectionForUser(@Nullable Long userId) {
        if (!store.available() || userId == null) return false;
        deleteAllForUser(userId);
        return true;
    }

    public void remember(String sessionId,
                         String content,
                         MemoryType memoryType,
                         float importance,
                         @Nullable Long userId) {
        if (!store.available()) return;
        try {
            long id = nextId();
            String text = truncate(content, 4000);
            Document doc = Document.newBuilder()
                    .withId(String.valueOf(id))
                    .addDocField(new DocField("text", text))
                    .addDocField(new DocField("user_id", userId == null ? "" : String.valueOf(userId)))
                    .addDocField(new DocField("session_id", sessionId == null ? "" : sessionId))
                    .addDocField(new DocField("memory_type", memoryType.name()))
                    .addDocField(new DocField("importance", importance))
                    .addDocField(new DocField("no_compress", false))
                    .addDocField(new DocField("create_time_ms", System.currentTimeMillis()))
                    .build();
            store.client().upsert(store.database(), store.memoryCollection(),
                    InsertParam.newBuilder().addDocument(doc).withBuildIndex(true).build());
            log.debug("[Memory] 已写入 {} session={}", memoryType, sessionId);
        } catch (Exception e) {
            log.warn("[Memory] 写入失败 session {}: {}", sessionId, e.getMessage());
        }
    }

    public void remember(String sessionId, String content, MemoryType memoryType, float importance) {
        remember(sessionId, content, memoryType, importance, null);
    }

    public List<MemoryRecord> recall(String queryText, int topK, @Nullable Long userId) {
        return search(queryText, topK, filter(null, null, userId));
    }

    public List<MemoryRecord> recall(String queryText, int topK) {
        return recall(queryText, topK, null);
    }

    public List<MemoryRecord> recallFromSession(String queryText, String sessionId, int topK, @Nullable Long userId) {
        return search(queryText, topK, filter(null, sessionId, userId));
    }

    public List<MemoryRecord> recallFromSession(String queryText, String sessionId, int topK) {
        return recallFromSession(queryText, sessionId, topK, null);
    }

    public List<MemoryRecord> recallUserSemantic(String queryText, int topK, Long userId) {
        if (userId == null) return List.of();
        return search(queryText, topK, filter(MemoryType.SEMANTIC, null, userId));
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

    public List<MemoryItem> listMemories(MemoryType memoryType,
                                         String sessionId,
                                         String keyword,
                                         long offset,
                                         int limit,
                                         @Nullable Long userId) {
        if (!store.available()) return List.of();
        long safeOffset = Math.max(0, offset);
        int pageSize = Math.min(limit, 100);
        int fetchLimit = (int) Math.min(safeOffset + pageSize, 1000);
        try {
            QueryParam.Builder query = QueryParam.newBuilder()
                    .withLimit(fetchLimit)
                    .withOffset(0)
                    .withOutputFields(LIST_FIELDS)
                    .withRetrieveVector(false);
            String expr = filter(memoryType, sessionId, userId);
            if (expr != null) query.withFilter(new Filter(expr));
            List<Document> docs = store.client().query(store.database(), store.memoryCollection(), query.build());
            List<MemoryItemWithTime> withTime = new ArrayList<>();
            for (Document doc : docs) {
                String content = textOf(doc);
                if (keyword != null && !keyword.isBlank() && !content.toLowerCase().contains(keyword.toLowerCase())) {
                    continue;
                }
                long createTimeMs = longOf(doc, "create_time_ms");
                withTime.add(new MemoryItemWithTime(toItem(doc, null), createTimeMs));
            }
            withTime.sort((a, b) -> Long.compare(b.createTimeMs, a.createTimeMs));
            int from = (int) Math.min(safeOffset, withTime.size());
            int to = Math.min(from + pageSize, withTime.size());
            List<MemoryItem> page = new ArrayList<>();
            for (int i = from; i < to; i++) page.add(withTime.get(i).item);
            return page;
        } catch (Exception e) {
            log.warn("[Memory] 列表失败: {}", e.getMessage());
            return List.of();
        }
    }

    public long countMemories(MemoryType memoryType, String sessionId, @Nullable Long userId) {
        if (!store.available()) return 0L;
        try {
            CountQueryParam.Builder count = CountQueryParam.newBuilder();
            String expr = filter(memoryType, sessionId, userId);
            if (expr != null) count.withFilter(expr);
            return store.client().count(store.database(), store.memoryCollection(), count.build()).getCount();
        } catch (Exception e) {
            log.warn("[Memory] 计数失败: {}", e.getMessage());
            return 0L;
        }
    }

    public List<MemoryItem> listMemoriesByIds(List<String> ids, @Nullable Long userId) {
        if (!store.available() || ids == null || ids.isEmpty() || userId == null) return List.of();
        List<String> parsed = new ArrayList<>();
        for (String s : ids) {
            try {
                parsed.add(String.valueOf(Long.parseLong(s.trim())));
            } catch (NumberFormatException ignored) {
            }
        }
        if (parsed.isEmpty()) return List.of();
        try {
            QueryParam query = QueryParam.newBuilder()
                    .withDocumentIds(parsed)
                    .withFilter(new Filter(filter(null, null, userId)))
                    .withOutputFields(LIST_FIELDS)
                    .withLimit(parsed.size())
                    .withRetrieveVector(false)
                    .build();
            List<Document> docs = store.client().query(store.database(), store.memoryCollection(), query);
            List<MemoryItem> out = new ArrayList<>();
            for (Document doc : docs) out.add(toItem(doc, null));
            return out;
        } catch (Exception e) {
            log.warn("[Memory] 按 id 查询失败: {}", e.getMessage());
            return List.of();
        }
    }

    public List<MemoryItem> searchMemories(String queryText, int topK, @Nullable Long userId) {
        if (!store.available()) return List.of();
        try {
            SearchByEmbeddingItemsParam.Builder search = SearchByEmbeddingItemsParam.newBuilder()
                    .withEmbeddingItems(List.of(queryText))
                    .withParams(new HNSWSearchParams(100))
                    .withLimit(topK)
                    .withOutputFields(LIST_FIELDS);
            String expr = filter(null, null, userId);
            if (expr != null) search.withFilter(new Filter(expr));
            SearchRes res = store.client().searchByEmbeddingItems(
                    store.database(), store.memoryCollection(), search.build());
            List<MemoryItem> results = new ArrayList<>();
            if (res == null || res.getDocuments() == null) return results;
            for (List<Document> row : res.getDocuments()) {
                for (Document doc : row) {
                    Double score = doc.getScore();
                    results.add(toItem(doc, score));
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("[Memory] 搜索失败: {}", e.getMessage());
            return List.of();
        }
    }

    public List<MemoryItem> searchMemories(String queryText, int topK) {
        return searchMemories(queryText, topK, null);
    }

    public void deleteById(long id, @Nullable Long userId) {
        if (!store.available()) return;
        String expr = userId == null
                ? "id=\"" + id + "\""
                : "id=\"" + id + "\" and " + filter(null, null, userId);
        store.client().delete(store.database(), store.memoryCollection(),
                DeleteParam.newBuilder().withFilter(new Filter(expr)).build());
        log.info("[Memory] 已删除 id={}", id);
    }

    public boolean updateNoCompress(long id, boolean noCompress, @Nullable Long userId) {
        return updateField(id, userId, new DocField("no_compress", noCompress));
    }

    public boolean updateImportance(long id, float importance, @Nullable Long userId) {
        return updateField(id, userId, new DocField("importance", importance));
    }

    public long deleteBySession(String sessionId, @Nullable Long userId) {
        if (!store.available()) return 0L;
        String expr = filter(null, sessionId, userId);
        if (expr == null) return 0L;
        store.client().delete(store.database(), store.memoryCollection(),
                DeleteParam.newBuilder().withFilter(new Filter(expr)).build());
        log.info("[Memory] 已删除会话 {}", sessionId);
        return 0;
    }

    public void deleteByType(MemoryType memoryType, @Nullable Long userId) {
        if (!store.available()) return;
        String expr = filter(memoryType, null, userId);
        if (expr == null) return;
        store.client().delete(store.database(), store.memoryCollection(),
                DeleteParam.newBuilder().withFilter(new Filter(expr)).build());
        log.info("[Memory] 已删除类型 {}", memoryType);
    }

    public void deleteAllForUser(@Nullable Long userId) {
        if (!store.available() || userId == null) return;
        store.client().delete(store.database(), store.memoryCollection(),
                DeleteParam.newBuilder().withFilter(new Filter(filter(null, null, userId))).build());
        log.info("[Memory] 已清空用户 {}", userId);
    }

    private boolean updateField(long id, @Nullable Long userId, DocField field) {
        if (!store.available() || userId == null) return false;
        try {
            UpdateParam param = UpdateParam.newBuilder()
                    .addAllDocumentId(List.of(String.valueOf(id)))
                    .withFilter(new Filter(filter(null, null, userId)))
                    .build();
            store.client().update(store.database(), store.memoryCollection(), param,
                    Document.newBuilder().addDocField(field).build());
            return true;
        } catch (Exception e) {
            log.warn("[Memory] 更新失败 id={}: {}", id, e.getMessage());
            return false;
        }
    }

    private List<MemoryRecord> search(String queryText, int topK, @Nullable String expr) {
        if (!store.available() || queryText == null || queryText.isBlank()) return List.of();
        try {
            SearchByEmbeddingItemsParam.Builder search = SearchByEmbeddingItemsParam.newBuilder()
                    .withEmbeddingItems(List.of(queryText))
                    .withParams(new HNSWSearchParams(100))
                    .withLimit(topK)
                    .withOutputFields(List.of("text", "memory_type", "session_id", "importance"));
            if (expr != null) search.withFilter(new Filter(expr));
            SearchRes res = store.client().searchByEmbeddingItems(
                    store.database(), store.memoryCollection(), search.build());
            List<MemoryRecord> results = new ArrayList<>();
            if (res == null || res.getDocuments() == null) return results;
            for (List<Document> row : res.getDocuments()) {
                for (Document doc : row) {
                    double score = doc.getScore() == null ? 0.0 : doc.getScore();
                    if (score < MIN_SCORE_THRESHOLD) continue;
                    results.add(new MemoryRecord(
                            textOf(doc),
                            memType(doc),
                            str(doc, "session_id"),
                            num(doc, "importance"),
                            score));
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("[Memory] 召回失败: {}", e.getMessage());
            return List.of();
        }
    }

    private MemoryItem toItem(Document doc, Double score) {
        long id = 0L;
        try { id = Long.parseLong(doc.getId()); } catch (Exception ignored) { }
        long createTimeMs = longOf(doc, "create_time_ms");
        String createTimeStr = createTimeMs > 0 ? TIME_FMT.format(Instant.ofEpochMilli(createTimeMs)) : "-";
        return new MemoryItem(
                id,
                str(doc, "session_id"),
                textOf(doc),
                memType(doc),
                num(doc, "importance"),
                bool(doc, "no_compress"),
                createTimeStr,
                score
        );
    }

    /** 腾讯云过滤表达式。用户、类型、会话都加上，避免共享集合串数据。 */
    private String filter(@Nullable MemoryType memoryType, @Nullable String sessionId, @Nullable Long userId) {
        List<String> parts = new ArrayList<>();
        if (userId != null) parts.add("user_id=\"" + userId + "\"");
        if (memoryType != null) parts.add("memory_type=\"" + memoryType.name() + "\"");
        if (sessionId != null && !sessionId.isBlank()) parts.add("session_id=\"" + escape(sessionId) + "\"");
        return parts.isEmpty() ? null : String.join(" and ", parts);
    }

    private static String textOf(Document doc) {
        Object text = doc.getObject("text");
        return text == null ? "" : text.toString();
    }

    private static String str(Document doc, String key) {
        Object v = doc.getObject(key);
        return v == null ? "" : v.toString();
    }

    private static float num(Document doc, String key) {
        Object v = doc.getObject(key);
        return v instanceof Number n ? n.floatValue() : 0f;
    }

    private static long longOf(Document doc, String key) {
        Object v = doc.getObject(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static boolean bool(Document doc, String key) {
        Object v = doc.getObject(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return v != null && Boolean.parseBoolean(v.toString());
    }

    private static MemoryType memType(Document doc) {
        try { return MemoryType.valueOf(str(doc, "memory_type")); }
        catch (Exception ex) { return MemoryType.EPISODIC; }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static long nextId() {
        long id = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        return id == 0 ? 1 : id;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }

    private static final class MemoryItemWithTime {
        final MemoryItem item;
        final long createTimeMs;

        private MemoryItemWithTime(MemoryItem item, long createTimeMs) {
            this.item = item;
            this.createTimeMs = createTimeMs;
        }
    }
}
