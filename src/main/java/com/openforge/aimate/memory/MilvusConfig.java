package com.openforge.aimate.memory;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Milvus infrastructure bean configuration.
 *
 * On startup:
 *   1. Creates a MilvusClientV2 connected to the configured host:port
 *   2. Checks if the "agent_memories" collection exists
 *   3. Creates it (with schema + HNSW index) if it doesn't
 *
 * Collection schema  (agent_memories):
 * ┌──────────────────┬─────────────────┬─────────────────────────────────────┐
 * │ Field            │ Type            │ Notes                               │
 * ├──────────────────┼─────────────────┼─────────────────────────────────────┤
 * │ id               │ INT64 PK        │ auto_id = true                      │
 * │ user_id          │ VARCHAR(64)     │ which user this memory belongs to   │
 * │ session_id       │ VARCHAR(64)     │ which session created this memory   │
 * │ content          │ VARCHAR(4096)   │ the actual memory text              │
 * │ memory_type      │ VARCHAR(32)     │ EPISODIC / SEMANTIC / PROCEDURAL    │
 * │ importance       │ FLOAT           │ 0.0 – 1.0                           │
 * │ create_time_ms   │ INT64           │ epoch millis                        │
 * │ embedding        │ FLOAT_VECTOR    │ dim = vectorDimensions (1536)       │
 * └──────────────────┴─────────────────┴─────────────────────────────────────┘
 *
 * Index: HNSW on embedding, metric = IP (inner product ≈ cosine similarity
 * for normalized vectors; OpenAI embeddings are already L2-normalized).
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MilvusProperties.class)
@ConditionalOnProperty(name = "agent.milvus.enabled", havingValue = "true", matchIfMissing = true)
public class MilvusConfig {

    @Bean(destroyMethod = "close")
    @Nullable
    public MilvusClientV2 milvusClient(MilvusProperties props) {
        log.info("[Milvus] Connecting to {}:{}...", props.host(), props.port());
        try {
            MilvusClientV2 client = new MilvusClientV2(
                    ConnectConfig.builder()
                            .uri("http://%s:%d".formatted(props.host(), props.port()))
                            .connectTimeoutMs(15_000)
                            .build()
            );
            log.info("[Milvus] Connected successfully.");
            ensureCollectionExists(client, props);
            return client;
        } catch (Exception e) {
            log.warn("[Milvus] Connection failed — long-term memory will be DISABLED. " +
                     "Cause: {}. " +
                     "If using FRP, ensure the Milvus port uses [type = tcp] not [type = http]. " +
                     "To suppress this warning, set agent.milvus.enabled=false.",
                    e.getMessage());
            return null;
        }
    }

    // ── Collection bootstrap ─────────────────────────────────────────────────

    private void ensureCollectionExists(MilvusClientV2 client, MilvusProperties props) {
        String name = props.collectionName();

        boolean exists = client.hasCollection(
                HasCollectionReq.builder().collectionName(name).build());

        if (exists) {
            log.info("[Milvus] Collection '{}' already exists — skipping creation.", name);
            return;
        }

        log.info("[Milvus] Creating collection '{}' (dim={})...", name, props.vectorDimensions());

        // ── Schema ───────────────────────────────────────────────────────────
        CreateCollectionReq.CollectionSchema schema =
                CreateCollectionReq.CollectionSchema.builder().build();

        schema.addField(AddFieldReq.builder()
                .fieldName("id")
                .dataType(DataType.Int64)
                .isPrimaryKey(true)
                .autoID(true)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("user_id")
                .dataType(DataType.VarChar)
                .maxLength(64)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("session_id")
                .dataType(DataType.VarChar)
                .maxLength(64)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("content")
                .dataType(DataType.VarChar)
                .maxLength(4096)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("memory_type")
                .dataType(DataType.VarChar)
                .maxLength(32)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("importance")
                .dataType(DataType.Float)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("create_time_ms")
                .dataType(DataType.Int64)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("embedding")
                .dataType(DataType.FloatVector)
                .dimension(props.vectorDimensions())
                .build());

        // ── HNSW index on the vector field ───────────────────────────────────
        // IP (Inner Product) is the correct metric for OpenAI normalized vectors.
        IndexParam vectorIndex = IndexParam.builder()
                .fieldName("embedding")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.IP)
                .extraParams(java.util.Map.of("M", 16, "efConstruction", 256))
                .build();

        // ── Scalar index on user_id / session_id for filtered recall ────────
        IndexParam userIndex = IndexParam.builder()
                .fieldName("user_id")
                .indexType(IndexParam.IndexType.TRIE)
                .build();

        IndexParam sessionIndex = IndexParam.builder()
                .fieldName("session_id")
                .indexType(IndexParam.IndexType.TRIE)
                .build();

        client.createCollection(CreateCollectionReq.builder()
                .collectionName(name)
                .collectionSchema(schema)
                .indexParams(List.of(vectorIndex, userIndex, sessionIndex))
                .build());

        log.info("[Milvus] Collection '{}' created successfully.", name);
    }
}
