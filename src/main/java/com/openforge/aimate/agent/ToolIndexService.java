package com.openforge.aimate.agent;

import com.openforge.aimate.domain.AgentTool;
import com.openforge.aimate.memory.TencentVectorStore;
import com.openforge.aimate.repository.AgentToolRepository;
import com.tencent.tcvectordb.model.DocField;
import com.tencent.tcvectordb.model.Document;
import com.tencent.tcvectordb.model.param.dml.HNSWSearchParams;
import com.tencent.tcvectordb.model.param.dml.DeleteParam;
import com.tencent.tcvectordb.model.param.dml.Filter;
import com.tencent.tcvectordb.model.param.dml.InsertParam;
import com.tencent.tcvectordb.model.param.dml.SearchByEmbeddingItemsParam;
import com.tencent.tcvectordb.model.param.entity.SearchRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具语义索引。与长期记忆一样，文本交给腾讯云向量库的内置向量模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolIndexService {

    private static final int MAX_TOOL_TEXT_LEN = 3500;

    private final TencentVectorStore store;
    private final AgentToolRepository toolRepository;
    private final Set<String> populatedKeys = ConcurrentHashMap.newKeySet();

    public List<String> searchRelevantTools(String queryText, int topK, @Nullable Long userId) {
        if (!store.available() || queryText == null || queryText.isBlank()) return List.of();
        try {
            String popKey = userId != null ? String.valueOf(userId) : "system";
            if (populatedKeys.add(popKey)) {
                indexAllTools(userId);
            }
            SearchByEmbeddingItemsParam search = SearchByEmbeddingItemsParam.newBuilder()
                    .withEmbeddingItems(List.of(queryText))
                    .withParams(new HNSWSearchParams(100))
                    .withLimit(Math.min(topK, 50))
                    .withOutputFields(List.of("tool_id"))
                    .build();
            SearchRes res = store.client().searchByEmbeddingItems(
                    store.database(), store.toolCollection(), search);
            if (res == null || res.getDocuments() == null) return List.of();
            List<String> ids = new ArrayList<>();
            for (List<Document> row : res.getDocuments()) {
                for (Document doc : row) {
                    Object id = doc.getObject("tool_id");
                    if (id == null || id.toString().isBlank()) id = doc.getId();
                    if (id != null && !id.toString().isBlank()) ids.add(id.toString());
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("[ToolIndex] 检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    public void indexToolByName(String toolName, @Nullable Long userId) {
        if (!store.available() || toolName == null || toolName.isBlank()) return;
        var opt = userId != null
                ? toolRepository.findByToolNameAndUserId(toolName, userId)
                : toolRepository.findByToolNameAndUserIdIsNull(toolName);
        if (opt.isEmpty()) return;
        indexOne(opt.get());
    }

    private void indexAllTools(@Nullable Long userId) {
        for (AgentTool t : toolRepository.findByUserIdIsNullAndIsActiveTrue()) indexOne(t);
        if (userId != null) {
            for (AgentTool t : toolRepository.findByUserIdAndIsActiveTrue(userId)) indexOne(t);
        }
    }

    private void indexOne(AgentTool t) {
        try {
            String schemaText = t.getInputSchema() == null ? "" : t.getInputSchema();
            if (schemaText.length() > 2000) schemaText = schemaText.substring(0, 2000) + "...";
            String description = t.getToolDescription() == null ? "" : t.getToolDescription();
            String text = t.getToolName() + "\n" + description + "\n" + schemaText;
            if (text.length() > MAX_TOOL_TEXT_LEN) text = text.substring(0, MAX_TOOL_TEXT_LEN);
            String toolId = t.getToolName();
            store.client().delete(store.database(), store.toolCollection(),
                    DeleteParam.newBuilder()
                            .withFilter(new Filter("tool_id=\"" + toolId.replace("\"", "") + "\""))
                            .build());
            Document doc = Document.newBuilder()
                    .withId(toolId)
                    .addDocField(new DocField("text", text))
                    .addDocField(new DocField("tool_id", toolId))
                    .addDocField(new DocField("tool_name", toolId))
                    .build();
            store.client().upsert(store.database(), store.toolCollection(),
                    InsertParam.newBuilder().addDocument(doc).withBuildIndex(true).build());
        } catch (Exception e) {
            log.warn("[ToolIndex] 写入失败 {}: {}", t.getToolName(), e.getMessage());
        }
    }
}
