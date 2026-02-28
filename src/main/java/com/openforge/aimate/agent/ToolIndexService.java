package com.openforge.aimate.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.openforge.aimate.domain.AgentTool;
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

    /** Dimensions for which we have already written tool index (avoids re-index on every search). */
    private final Set<Integer> populatedDimensions = ConcurrentHashMap.newKeySet();

    /**
     * Returns tool IDs most relevant to the query. Uses the current user's embedding
     * when userId is set (same as memories), so no OpenAI key is used if user has Ollama etc.
     */
    public List<String> searchRelevantTools(String queryText, int topK, @Nullable Long userId) {
        if (milvusClient == null || queryText == null || queryText.isBlank()) {
            return List.of();
        }
        try {
            EmbeddingClient client;
            int dimension;
            boolean useUserEmbedding = false;
            if (userId != null) {
                var resolved = embeddingResolver.resolveDefault(userId);
                if (resolved.isPresent()) {
                    var r = resolved.get();
                    dimension = r.dimension();
                    client = new EmbeddingClient(httpClient, objectMapper, r.props());
                    useUserEmbedding = true;
                } else {
                    dimension = milvusProperties.vectorDimensions();
                    client = null; // skip system embedding to avoid OpenAI 401 when user has no config
                }
            } else {
                dimension = milvusProperties.vectorDimensions();
                client = null;
            }

            if (client == null) {
                return List.of();
            }

            String collectionName = MilvusCollectionManager.toolIndexCollectionName(dimension);
            if (!collectionManager.ensureToolIndexCollection(dimension)) return List.of();

            if (useUserEmbedding && !populatedDimensions.contains(dimension)) {
                indexAllToolsWith(client, dimension);
                populatedDimensions.add(dimension);
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

    private void indexAllToolsWith(EmbeddingClient client, int dimension) {
        String coll = MilvusCollectionManager.toolIndexCollectionName(dimension);
        indexToolInto(client, coll, "recall_memory", "recall_memory",
                "Search long-term memory by natural language query. Returns relevant past information (e.g. user profile, name, preferences). Use when you need to look up something that may have been stored before.",
                "query: string (required). top_k: optional integer, default 10.");
        indexToolInto(client, coll, "store_memory", "store_memory",
                "Store an IMPORTANT, long-term piece of information into memory for future sessions. Use sparingly. Only call this for facts that will matter across many tasks, not for one-off details.",
                "content: string (required). memory_type: EPISODIC | SEMANTIC | PROCEDURAL. importance: 0-1.");
        toolRepository.findByIsActiveTrue().forEach(t -> {
            String schemaText = t.getInputSchema() != null && t.getInputSchema().length() <= 2000
                    ? t.getInputSchema()
                    : (t.getInputSchema() != null ? t.getInputSchema().substring(0, 2000) + "..." : "");
            indexToolInto(client, coll, t.getToolName(), t.getToolName(), t.getToolDescription(), schemaText);
        });
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
