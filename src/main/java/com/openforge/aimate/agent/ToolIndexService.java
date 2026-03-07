package com.openforge.aimate.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.openforge.aimate.embedding.UserEmbeddingResolver;
import com.openforge.aimate.memory.EmbeddingClient;
import com.openforge.aimate.memory.MilvusCollectionManager;
import com.openforge.aimate.memory.MilvusProperties;
import com.openforge.aimate.repository.AgentToolRepository;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.openforge.aimate.domain.AgentTool;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vector index for agent tools: semantic search by user intent.
 * Uses the current user's embedding model for search (same as memories), so we never
 * call the system OpenAI embedding when the user has their own model configured.
 *
 * One tool index collection per dimension (e.g. agent_tools_index_1024 for bge-m3).
 * Lazy-populate when first search for that dimension runs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolIndexService {

    private static final int MAX_TOOL_TEXT_LEN = 3500;

    @Nullable
    private final MilvusClientV2 milvusClient;
    private final EmbeddingClient embeddingClient;
    private final MilvusProperties milvusProperties;
    private final MilvusCollectionManager collectionManager;
    private final AgentToolRepository toolRepository;
    private final UserEmbeddingResolver embeddingResolver;
    private final HttpClient httpClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** (dimension, userId) 已写入索引的 key，避免重复全量索引。 */
    private final Set<String> populatedKeys = ConcurrentHashMap.newKeySet();

    /**
     * Returns tool IDs most relevant to the query. Uses the current user's embedding
     * when userId is set (same as memories), so no OpenAI key is used if user has Ollama etc.
     * Indexes system tools + current user's tools on first search for that (dimension, userId).
     */
    public List<String> searchRelevantTools(String queryText, int topK, @Nullable Long userId) {
        if (milvusClient == null || queryText == null || queryText.isBlank()) {
            return List.of();
        }
        try {
            EmbeddingClient client;
            int dimension;
            if (userId != null) {
                var resolved = embeddingResolver.resolveDefault(userId);
                if (resolved.isPresent()) {
                    var r = resolved.get();
                    dimension = r.dimension();
                    client = new EmbeddingClient(httpClient, objectMapper, r.props());
                } else {
                    dimension = milvusProperties.vectorDimensions();
                    client = embeddingClient; // 用户未配置时使用系统默认（127.0.0.1 产线向量服务）
                }
            } else {
                dimension = milvusProperties.vectorDimensions();
                client = embeddingClient;
            }

            String collectionName = MilvusCollectionManager.toolIndexCollectionName(dimension);
            if (!collectionManager.ensureToolIndexCollection(dimension)) return List.of();

            String popKey = dimension + "_" + (userId != null ? userId : "null");
            if (!populatedKeys.contains(popKey)) {
                indexAllToolsWith(client, dimension, userId);
                populatedKeys.add(popKey);
            }

            List<Float> vector = client.embed(queryText);
            SearchResp resp = milvusClient.search(SearchReq.builder()
                    .collectionName(collectionName)
                    .data(List.of(new FloatVec(vector)))
                    .annsField("embedding")
                    .topK(Math.min(topK, 50))
                    .outputFields(List.of("tool_id"))
                    .build());
            if (resp == null || resp.getSearchResults() == null) return List.of();
            List<String> ids = new ArrayList<>();
            for (List<SearchResp.SearchResult> row : resp.getSearchResults()) {
                for (SearchResp.SearchResult hit : row) {
                    Object id = hit.getEntity() != null ? hit.getEntity().get("tool_id") : null;
                    if (id != null && !id.toString().isBlank()) ids.add(id.toString());
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("[ToolIndex] Search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Index a single tool by name (e.g. after create_tool). Uses the same embedding resolution as
     * searchRelevantTools so the tool is findable in the next semantic search.
     * User tool: lookup by toolName + userId; system tool: lookup by toolName + userId null.
     */
    public void indexToolByName(String toolName, @Nullable Long userId) {
        if (milvusClient == null || toolName == null || toolName.isBlank()) return;
        var opt = userId != null
                ? toolRepository.findByToolNameAndUserId(toolName, userId)
                : toolRepository.findByToolNameAndUserIdIsNull(toolName);
        if (opt.isEmpty()) return;
        AgentTool t = opt.get();
        EmbeddingClient client;
        int dimension;
        try {
            if (userId != null) {
                var resolved = embeddingResolver.resolveDefault(userId);
                if (resolved.isPresent()) {
                    var r = resolved.get();
                    dimension = r.dimension();
                    client = new EmbeddingClient(httpClient, objectMapper, r.props());
                } else {
                    dimension = milvusProperties.vectorDimensions();
                    client = embeddingClient; // 用户未配置时使用系统默认（127.0.0.1）
                }
            } else {
                dimension = milvusProperties.vectorDimensions();
                client = embeddingClient;
            }
        } catch (Exception e) {
            log.warn("[ToolIndex] Cannot resolve embedding for indexToolByName: {}", e.getMessage());
            return;
        }
        if (!collectionManager.ensureToolIndexCollection(dimension)) return;
        String coll = MilvusCollectionManager.toolIndexCollectionName(dimension);
        String schemaText = t.getInputSchema() != null && t.getInputSchema().length() <= 2000
                ? t.getInputSchema()
                : (t.getInputSchema() != null ? t.getInputSchema().substring(0, 2000) + "..." : "");
        indexToolInto(client, coll, t.getToolName(), t.getToolName(), t.getToolDescription(), schemaText);
        log.debug("[ToolIndex] Indexed tool: {}", toolName);
    }

    private void indexAllToolsWith(EmbeddingClient client, int dimension, @Nullable Long userId) {
        String coll = MilvusCollectionManager.toolIndexCollectionName(dimension);
        List<AgentTool> systemTools = toolRepository.findByUserIdIsNullAndIsActiveTrue();
        List<AgentTool> userTools = userId != null ? toolRepository.findByUserIdAndIsActiveTrue(userId) : List.of();
        for (AgentTool t : systemTools) {
            String schemaText = t.getInputSchema() != null && t.getInputSchema().length() <= 2000
                    ? t.getInputSchema()
                    : (t.getInputSchema() != null ? t.getInputSchema().substring(0, 2000) + "..." : "");
            indexToolInto(client, coll, t.getToolName(), t.getToolName(), t.getToolDescription(), schemaText);
        }
        for (AgentTool t : userTools) {
            String schemaText = t.getInputSchema() != null && t.getInputSchema().length() <= 2000
                    ? t.getInputSchema()
                    : (t.getInputSchema() != null ? t.getInputSchema().substring(0, 2000) + "..." : "");
            indexToolInto(client, coll, t.getToolName(), t.getToolName(), t.getToolDescription(), schemaText);
        }
    }

    private void indexToolInto(EmbeddingClient client, String collectionName,
                              String toolId, String toolName, String description, String schemaText) {
        try {
            String textToEmbed = (toolName + "\n" + description + "\n" + schemaText);
            if (textToEmbed.length() > MAX_TOOL_TEXT_LEN) textToEmbed = textToEmbed.substring(0, MAX_TOOL_TEXT_LEN);
            List<Float> vector = client.embed(textToEmbed);

            milvusClient.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter("tool_id == \"%s\"".formatted(toolId.replace("\"", "\\\"")))
                    .build());

            JsonObject row = new JsonObject();
            row.addProperty("tool_id", toolId);
            row.addProperty("tool_name", toolName);
            row.addProperty("description", description.length() > 2040 ? description.substring(0, 2040) : description);
            row.addProperty("schema_text", schemaText.length() > 4090 ? schemaText.substring(0, 4090) : schemaText);
            JsonArray arr = new JsonArray();
            for (Float f : vector) arr.add(f);
            row.add("embedding", arr);

            milvusClient.insert(InsertReq.builder()
                    .collectionName(collectionName)
                    .data(List.of(row))
                    .build());
        } catch (Exception e) {
            log.warn("[ToolIndex] Failed to index tool {}: {}", toolId, e.getMessage());
        }
    }
}
