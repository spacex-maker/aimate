package com.openforge.aimate.memory;

import com.tencent.tcvectordb.client.VectorDBClient;
import com.tencent.tcvectordb.model.param.collection.CreateCollectionParam;
import com.tencent.tcvectordb.model.param.collection.Embedding;
import com.tencent.tcvectordb.model.param.collection.FieldType;
import com.tencent.tcvectordb.model.param.collection.FilterIndex;
import com.tencent.tcvectordb.model.param.collection.HNSWParams;
import com.tencent.tcvectordb.model.param.collection.IndexType;
import com.tencent.tcvectordb.model.param.collection.MetricType;
import com.tencent.tcvectordb.model.param.collection.VectorIndex;
import com.tencent.tcvectordb.model.param.database.ConnectParam;
import com.tencent.tcvectordb.model.param.enums.ReadConsistencyEnum;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 腾讯云向量库连接。集合使用库内向量模型，写入文本即可，不必在应用侧算向量。
 */
@Slf4j
@Component
public class TencentVectorStore {

    private final VectorDbProperties props;
    private final VectorDBClient client;

    public TencentVectorStore(VectorDbProperties props) {
        this.props = props;
        VectorDBClient connected = null;
        if (!props.configured()) {
            log.warn("[VectorDB] 未配置 url/key，长期记忆与工具向量检索关闭。");
        } else {
            try {
                ConnectParam connect = ConnectParam.newBuilder()
                        .withUrl(props.url())
                        .withUsername(props.username())
                        .withKey(props.key())
                        .withTimeout(props.timeoutSeconds())
                        .withConnectTimeout(Math.min(props.timeoutSeconds(), 10))
                        .withMaxIdleConnections(8)
                        .build();
                connected = new VectorDBClient(connect, ReadConsistencyEnum.EVENTUAL_CONSISTENCY);
                connected.createDatabaseIfNotExists(props.database());
                ensureCollection(connected, props.memoryCollection(), "长期记忆", true);
                ensureCollection(connected, props.toolCollection(), "工具语义索引", false);
                log.info("[VectorDB] 已连接 {} 库 {} 模型 {} dim={}",
                        props.url(), props.database(), props.embeddingModel(), props.vectorDimensions());
            } catch (Exception e) {
                log.warn("[VectorDB] 连接或建集合失败，记忆检索关闭: {}", e.getMessage());
                if (connected != null) {
                    try { connected.close(); } catch (Exception ignored) { }
                }
                connected = null;
            }
        }
        this.client = connected;
    }

    public boolean available() {
        return client != null;
    }

    public VectorDBClient client() {
        return client;
    }

    public VectorDbProperties props() {
        return props;
    }

    public String database() {
        return props.database();
    }

    public String memoryCollection() {
        return props.memoryCollection();
    }

    public String toolCollection() {
        return props.toolCollection();
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            try { client.close(); } catch (Exception ignored) { }
        }
    }

    private void ensureCollection(VectorDBClient db, String name, String description, boolean memory) {
        try {
            db.describeCollection(props.database(), name);
            log.info("[VectorDB] 集合 {} 已存在", name);
            return;
        } catch (Exception ignored) {
            // 不存在时创建
        }
        CreateCollectionParam.Builder builder = CreateCollectionParam.newBuilder()
                .withName(name)
                .withShardNum(1)
                .withReplicaNum(1)
                .withDescription(description)
                .addField(new FilterIndex("id", FieldType.String, IndexType.PRIMARY_KEY))
                .addField(new VectorIndex("vector", props.vectorDimensions(), IndexType.HNSW,
                        MetricType.COSINE, new HNSWParams(16, 200)))
                .withEmbedding(Embedding.newBuilder()
                        .withModelName(props.embeddingModel())
                        .withField("text")
                        .withVectorField("vector")
                        .build());
        if (memory) {
            builder.addField(new FilterIndex("user_id", FieldType.String, IndexType.FILTER))
                    .addField(new FilterIndex("session_id", FieldType.String, IndexType.FILTER))
                    .addField(new FilterIndex("memory_type", FieldType.String, IndexType.FILTER));
        } else {
            builder.addField(new FilterIndex("tool_id", FieldType.String, IndexType.FILTER));
        }
        db.createCollection(props.database(), builder.build());
        log.info("[VectorDB] 已创建集合 {} model={}", name, props.embeddingModel());
    }
}
