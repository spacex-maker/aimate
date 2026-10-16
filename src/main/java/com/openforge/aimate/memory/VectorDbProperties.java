package com.openforge.aimate.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 腾讯云向量数据库。向量由库内模型计算，应用不再单独请求 embedding 接口。
 */
@ConfigurationProperties(prefix = "agent.vectordb")
public record VectorDbProperties(
        boolean enabled,
        String url,
        String username,
        String key,
        String database,
        String memoryCollection,
        String toolCollection,
        String embeddingModel,
        int vectorDimensions,
        int timeoutSeconds
) {
    public VectorDbProperties {
        if (username == null || username.isBlank()) username = "root";
        if (database == null || database.isBlank()) database = "aimate";
        if (memoryCollection == null || memoryCollection.isBlank()) memoryCollection = "agent_memories";
        if (toolCollection == null || toolCollection.isBlank()) toolCollection = "agent_tools";
        if (embeddingModel == null || embeddingModel.isBlank()) embeddingModel = "bge-base-zh";
        if (vectorDimensions <= 0) vectorDimensions = 768;
        if (timeoutSeconds <= 0) timeoutSeconds = 30;
    }

    public boolean configured() {
        return enabled && url != null && !url.isBlank() && key != null && !key.isBlank();
    }
}
