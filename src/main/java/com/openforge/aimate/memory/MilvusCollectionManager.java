package com.openforge.aimate.memory;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Milvus collection lifecycle at runtime.
 *
 * Why this exists:
 *   - MilvusConfig creates the default system collection at startup.
 *   - When users configure their own embedding models (different dimensions),
 *     we need to create matching collections on demand.
 *   - This service caches known collection names to avoid repeated Milvus
 *     round-trips on every remember()/recall() call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusCollectionManager {

    @Nullable
    private final MilvusClientV2 milvusClient;

    /** Prefix for tool index collections; suffix is dimension (e.g. agent_tools_index_1536). */
    public static final String TOOL_INDEX_COLLECTION_PREFIX = "agent_tools_index_";

    public static String toolIndexCollectionName(int dimension) {
        return TOOL_INDEX_COLLECTION_PREFIX + dimension;
    }

    /** In-memory cache of collection names we know already exist. */
    private final Set<String> existingCollections = ConcurrentHashMap.newKeySet();

    /**
     * Ensures the tool index collection for the given dimension exists (one collection per dimension).
     */
    public boolean ensureToolIndexCollection(int dimension) {
        if (milvusClient == null) return false;
        String name = toolIndexCollectionName(dimension);
        if (existingCollections.contains(name)) return true;
        if (milvusClient.hasCollection(HasCollectionReq.builder().collectionName(name).build())) {
            existingCollections.add(name);
            return true;
        }
        log.info("[Milvus] Creating tool index collection '{}' (dim={})", name, dimension);
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
        schema.addField(AddFieldReq.builder().fieldName("tool_id")
                .dataType(DataType.VarChar).maxLength(128).isPrimaryKey(true).autoID(false).build());
        schema.addField(AddFieldReq.builder().fieldName("tool_name").dataType(DataType.VarChar).maxLength(256).build());
        schema.addField(AddFieldReq.builder().fieldName("description").dataType(DataType.VarChar).maxLength(2048).build());
        schema.addField(AddFieldReq.builder().fieldName("schema_text").dataType(DataType.VarChar).maxLength(4096).build());
        schema.addField(AddFieldReq.builder().fieldName("embedding").dataType(DataType.FloatVector).dimension(dimension).build());

        IndexParam vectorIndex = IndexParam.builder()
                .fieldName("embedding")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.IP)
                .extraParams(java.util.Map.of("M", 16, "efConstruction", 256))
                .build();
        milvusClient.createCollection(CreateCollectionReq.builder()
                .collectionName(name)
                .collectionSchema(schema)
                .indexParams(List.of(vectorIndex))
                .build());
        existingCollections.add(name);
        log.info("[Milvus] Tool index collection created.");
        return true;
    }

    /**
     * Ensures the given collection exists in Milvus.
     * No-op (and returns false) when Milvus is disabled.
     *
     * @param collectionName target collection name
     * @param dimension      embedding vector dimension
     * @return true if collection is ready to use
     */
    public boolean ensureCollection(String collectionName, int dimension) {
        if (milvusClient == null) return false;
        if (existingCollections.contains(collectionName)) return true;

        boolean exists = milvusClient.hasCollection(
                HasCollectionReq.builder().collectionName(collectionName).build());

        if (exists) {
            existingCollections.add(collectionName);
            log.info("[Milvus] Collection '{}' confirmed existing.", collectionName);
            return true;
        }

        log.info("[Milvus] Creating collection '{}' (dim={})…", collectionName, dimension);
        createCollection(collectionName, dimension);
        existingCollections.add(collectionName);
        return true;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void createCollection(String name, int dimension) {
        CreateCollectionReq.CollectionSchema schema =
                CreateCollectionReq.CollectionSchema.builder().build();

        schema.addField(AddFieldReq.builder().fieldName("id")
                .dataType(DataType.Int64).isPrimaryKey(true).autoID(true).build());
        schema.addField(AddFieldReq.builder().fieldName("session_id")
                .dataType(DataType.VarChar).maxLength(64).build());
        schema.addField(AddFieldReq.builder().fieldName("content")
                .dataType(DataType.VarChar).maxLength(4096).build());
        schema.addField(AddFieldReq.builder().fieldName("memory_type")
                .dataType(DataType.VarChar).maxLength(32).build());
        schema.addField(AddFieldReq.builder().fieldName("importance")
                .dataType(DataType.Float).build());
        schema.addField(AddFieldReq.builder().fieldName("create_time_ms")
                .dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("embedding")
                .dataType(DataType.FloatVector).dimension(dimension).build());

        IndexParam vectorIndex = IndexParam.builder()
                .fieldName("embedding")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.IP)
                .extraParams(java.util.Map.of("M", 16, "efConstruction", 256))
                .build();
        IndexParam sessionIndex = IndexParam.builder()
                .fieldName("session_id")
                .indexType(IndexParam.IndexType.TRIE)
                .build();

        milvusClient.createCollection(CreateCollectionReq.builder()
                .collectionName(name)
                .collectionSchema(schema)
                .indexParams(List.of(vectorIndex, sessionIndex))
                .build());

        log.info("[Milvus] Collection '{}' created successfully.", name);
    }
}
