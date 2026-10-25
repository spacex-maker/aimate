package com.openforge.aimate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.domain.AgentTool;
import com.openforge.aimate.memory.TencentVectorStore;
import com.openforge.aimate.repository.AgentToolRepository;
import com.tencent.tcvectordb.model.DocField;
import com.tencent.tcvectordb.model.Document;
import com.tencent.tcvectordb.model.param.dml.DeleteParam;
import com.tencent.tcvectordb.model.param.dml.Filter;
import com.tencent.tcvectordb.model.param.dml.HNSWSearchParams;
import com.tencent.tcvectordb.model.param.dml.InsertParam;
import com.tencent.tcvectordb.model.param.dml.SearchByEmbeddingItemsParam;
import com.tencent.tcvectordb.model.param.entity.SearchRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 工具语义索引与混合召回（对齐记忆系统：向量 + 分数阈值 + 词法精确/包含加分）。
 * <p>
 * 文档 id：{@code s:toolName}（官方） / {@code u{userId}:toolName}（Agent 工具箱），避免多用户同名冲突。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolIndexService {

    private static final int MAX_TOOL_TEXT_LEN = 3500;
    /** 与记忆召回类似：过低相似度直接丢弃，减少搜错 */
    private static final double MIN_VECTOR_SCORE = 0.42;
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s,_\\-./\\\\:;|，。；：、（）()\\[\\]{}「」『』]+");

    private final TencentVectorStore store;
    private final AgentToolRepository toolRepository;
    private final ObjectMapper objectMapper;
    private final Set<String> populatedKeys = ConcurrentHashMap.newKeySet();

    /** 召回命中：供 find_tools 展示与 loadRelevantTools 选工具。 */
    public record ToolHit(
            String toolName,
            String description,
            String toolType,
            boolean official,
            double score,
            String matchReason
    ) {}

    /**
     * 混合检索当前用户可见工具（官方 + 本人 Agent 工具箱）。
     * 优先级：精确工具名 &gt; 名称包含 &gt; 描述关键词 &gt; 向量相似（过阈值）。
     */
    public List<ToolHit> searchTools(String queryText, int topK, @Nullable Long userId) {
        String q = queryText == null ? "" : queryText.trim();
        if (q.isEmpty()) return List.of();
        int limit = Math.max(1, Math.min(topK, 20));

        Map<String, AgentTool> corpus = loadCorpus(userId);
        if (corpus.isEmpty()) return List.of();

        Map<String, ToolHit> merged = new HashMap<>();

        // 1) 词法：精确 / 包含 / token，保证「叫得出名字」一定能命中
        for (AgentTool t : corpus.values()) {
            LexScore lex = lexicalScore(q, t);
            if (lex.score <= 0) continue;
            merged.put(t.getToolName(), toHit(t, lex.score, lex.reason));
        }

        // 2) 向量：语义召回，再与词法取 max（同名不重复）
        ensureIndexed(userId);
        for (VecHit vh : vectorSearch(q, Math.min(limit * 4, 40))) {
            if (vh.score < MIN_VECTOR_SCORE) continue;
            AgentTool t = corpus.get(vh.toolName);
            if (t == null) continue;
            ToolHit prev = merged.get(t.getToolName());
            if (prev == null || vh.score > prev.score()) {
                merged.put(t.getToolName(), toHit(t, vh.score, "semantic"));
            }
        }

        return merged.values().stream()
                .sorted(Comparator.comparingDouble(ToolHit::score).reversed()
                        .thenComparing(ToolHit::toolName))
                .limit(limit)
                .toList();
    }

    /** 兼容旧接口：只返回工具名列表。 */
    public List<String> searchRelevantTools(String queryText, int topK, @Nullable Long userId) {
        return searchTools(queryText, topK, userId).stream().map(ToolHit::toolName).toList();
    }

    public void indexToolByName(String toolName, @Nullable Long userId) {
        if (!store.available() || toolName == null || toolName.isBlank()) return;
        var opt = userId != null
                ? toolRepository.findByToolNameAndUserId(toolName, userId)
                : toolRepository.findByToolNameAndUserIdIsNull(toolName);
        if (opt.isEmpty() && userId != null) {
            opt = toolRepository.findByToolNameAndUserIdIsNull(toolName);
        }
        if (opt.isEmpty()) return;
        indexOne(opt.get());
    }

    /** 强制刷新某用户可见工具索引（create_tool 后可调用）。 */
    public void reindexVisibleTools(@Nullable Long userId) {
        if (!store.available()) return;
        String popKey = userId != null ? String.valueOf(userId) : "system";
        populatedKeys.remove(popKey);
        ensureIndexed(userId);
    }

    private void ensureIndexed(@Nullable Long userId) {
        if (!store.available()) return;
        String popKey = userId != null ? String.valueOf(userId) : "system";
        if (populatedKeys.add(popKey)) {
            indexAllTools(userId);
        }
    }

    private Map<String, AgentTool> loadCorpus(@Nullable Long userId) {
        Map<String, AgentTool> map = new LinkedHashMap<>();
        for (AgentTool t : toolRepository.findByUserIdIsNullAndIsActiveTrue()) {
            if (t.getToolName() != null) map.putIfAbsent(t.getToolName(), t);
        }
        if (userId != null) {
            for (AgentTool t : toolRepository.findByUserIdAndIsActiveTrue(userId)) {
                if (t.getToolName() != null) map.put(t.getToolName(), t); // 用户工具覆盖同名（极少）
            }
        }
        return map;
    }

    private void indexAllTools(@Nullable Long userId) {
        for (AgentTool t : toolRepository.findByUserIdIsNullAndIsActiveTrue()) indexOne(t);
        if (userId != null) {
            for (AgentTool t : toolRepository.findByUserIdAndIsActiveTrue(userId)) indexOne(t);
        }
    }

    private void indexOne(AgentTool t) {
        if (!store.available() || t.getToolName() == null || t.getToolName().isBlank()) return;
        try {
            String text = buildIndexText(t);
            String docId = docId(t);
            String toolName = t.getToolName();
            // 清理旧版仅用 toolName 作 id 的文档 + 新文档
            tryDelete(toolName);
            tryDelete(docId);
            Document doc = Document.newBuilder()
                    .withId(docId)
                    .addDocField(new DocField("text", text))
                    .addDocField(new DocField("tool_id", toolName))
                    .addDocField(new DocField("tool_name", toolName))
                    .build();
            store.client().upsert(store.database(), store.toolCollection(),
                    InsertParam.newBuilder().addDocument(doc).withBuildIndex(true).build());
        } catch (Exception e) {
            log.warn("[ToolIndex] 写入失败 {}: {}", t.getToolName(), e.getMessage());
        }
    }

    private void tryDelete(String idOrFilterValue) {
        try {
            String safe = idOrFilterValue.replace("\"", "");
            store.client().delete(store.database(), store.toolCollection(),
                    DeleteParam.newBuilder()
                            .withFilter(new Filter("tool_id=\"" + safe + "\""))
                            .build());
        } catch (Exception ignored) {
        }
        try {
            store.client().delete(store.database(), store.toolCollection(),
                    DeleteParam.newBuilder()
                            .addAllDocumentId(List.of(idOrFilterValue))
                            .build());
        } catch (Exception ignored) {
        }
    }

    private String buildIndexText(AgentTool t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.getToolName()).append('\n');
        if (t.getToolDescription() != null) sb.append(t.getToolDescription()).append('\n');
        if (t.getToolType() != null) sb.append("type=").append(t.getToolType().name()).append('\n');
        String props = extractSchemaPropertyNames(t.getInputSchema());
        if (!props.isBlank()) sb.append("params: ").append(props).append('\n');
        String schemaText = t.getInputSchema() == null ? "" : t.getInputSchema();
        if (schemaText.length() > 1500) schemaText = schemaText.substring(0, 1500) + "...";
        sb.append(schemaText);
        String text = sb.toString();
        if (text.length() > MAX_TOOL_TEXT_LEN) text = text.substring(0, MAX_TOOL_TEXT_LEN);
        return text;
    }

    private String extractSchemaPropertyNames(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(schemaJson);
            JsonNode props = root.path("properties");
            if (!props.isObject()) return "";
            List<String> names = new ArrayList<>();
            props.fieldNames().forEachRemaining(names::add);
            return String.join(" ", names);
        } catch (Exception e) {
            return "";
        }
    }

    private static String docId(AgentTool t) {
        if (t.getUserId() == null) return "s:" + t.getToolName();
        return "u" + t.getUserId() + ":" + t.getToolName();
    }

    private record LexScore(double score, String reason) {}

    private LexScore lexicalScore(String query, AgentTool t) {
        String name = t.getToolName() == null ? "" : t.getToolName();
        String desc = t.getToolDescription() == null ? "" : t.getToolDescription();
        String nameL = name.toLowerCase(Locale.ROOT);
        String descL = desc.toLowerCase(Locale.ROOT);
        String qL = query.toLowerCase(Locale.ROOT);

        if (nameL.equals(qL) || name.equals(query)) {
            return new LexScore(1.0, "exact_name");
        }
        if (nameL.contains(qL) || qL.contains(nameL)) {
            return new LexScore(0.95, "name_contains");
        }
        // 去下划线后的模糊名：get_weather ↔ get weather / 天气
        String nameFlat = nameL.replace("_", "");
        String qFlat = qL.replace("_", "").replace(" ", "");
        if (!nameFlat.isEmpty() && (nameFlat.contains(qFlat) || qFlat.contains(nameFlat))) {
            return new LexScore(0.9, "name_fuzzy");
        }

        List<String> tokens = tokensOf(qL);
        if (tokens.isEmpty()) {
            if (descL.contains(qL)) return new LexScore(0.72, "desc_contains");
            return new LexScore(0, "");
        }

        int inName = 0;
        int inDesc = 0;
        for (String tok : tokens) {
            if (tok.length() < 2 && !isCjk(tok)) continue;
            if (nameL.contains(tok) || nameFlat.contains(tok.replace("_", ""))) inName++;
            else if (descL.contains(tok)) inDesc++;
        }
        int useful = 0;
        for (String tok : tokens) {
            if (tok.length() >= 2 || isCjk(tok)) useful++;
        }
        if (useful == 0) {
            if (descL.contains(qL)) return new LexScore(0.7, "desc_contains");
            return new LexScore(0, "");
        }
        if (inName == useful) return new LexScore(0.88, "all_tokens_in_name");
        if (inName > 0) return new LexScore(0.78, "token_in_name");
        if (inDesc == useful) return new LexScore(0.7, "all_tokens_in_desc");
        if (inDesc > 0) return new LexScore(0.58, "token_in_desc");
        if (descL.contains(qL)) return new LexScore(0.65, "desc_contains");
        return new LexScore(0, "");
    }

    private static boolean isCjk(String s) {
        if (s == null || s.isEmpty()) return false;
        int cp = s.codePointAt(0);
        return Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN;
    }

    private static List<String> tokensOf(String q) {
        List<String> out = new ArrayList<>();
        for (String p : TOKEN_SPLIT.split(q)) {
            if (p == null || p.isBlank()) continue;
            out.add(p.trim().toLowerCase(Locale.ROOT));
        }
        // 中文短查询：整句也作为 token
        if (out.isEmpty() && !q.isBlank()) out.add(q.trim().toLowerCase(Locale.ROOT));
        return out;
    }

    private record VecHit(String toolName, double score) {}

    private List<VecHit> vectorSearch(String query, int limit) {
        if (!store.available()) return List.of();
        try {
            SearchByEmbeddingItemsParam search = SearchByEmbeddingItemsParam.newBuilder()
                    .withEmbeddingItems(List.of(query))
                    .withParams(new HNSWSearchParams(100))
                    .withLimit(Math.min(limit, 50))
                    .withOutputFields(List.of("tool_id", "tool_name"))
                    .build();
            SearchRes res = store.client().searchByEmbeddingItems(
                    store.database(), store.toolCollection(), search);
            if (res == null || res.getDocuments() == null) return List.of();
            List<VecHit> hits = new ArrayList<>();
            for (List<Document> row : res.getDocuments()) {
                for (Document doc : row) {
                    Object id = doc.getObject("tool_name");
                    if (id == null || id.toString().isBlank()) id = doc.getObject("tool_id");
                    if (id == null || id.toString().isBlank()) {
                        String docId = doc.getId();
                        if (docId != null && docId.contains(":")) {
                            id = docId.substring(docId.indexOf(':') + 1);
                        } else {
                            id = docId;
                        }
                    }
                    if (id == null || id.toString().isBlank()) continue;
                    double score = doc.getScore() == null ? 0.0 : doc.getScore();
                    hits.add(new VecHit(id.toString(), score));
                }
            }
            return hits;
        } catch (Exception e) {
            log.warn("[ToolIndex] 向量检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    private static ToolHit toHit(AgentTool t, double score, String reason) {
        return new ToolHit(
                t.getToolName(),
                t.getToolDescription() == null ? "" : t.getToolDescription(),
                t.getToolType() != null ? t.getToolType().name() : "",
                t.getUserId() == null,
                score,
                reason
        );
    }
}
