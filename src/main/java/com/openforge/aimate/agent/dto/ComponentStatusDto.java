package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 各组件连接/配置状态，与启动摘要一致，供管理端容器监控页展示。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComponentStatusDto(
        ServerStatus server,
        MysqlStatus mysql,
        MilvusStatus milvus,
        LlmStatus llm,
        EmbeddingStatus embedding,
        DockerStatus docker
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ServerStatus(String port, String javaVersion, boolean virtualThreadEnabled) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MysqlStatus(boolean ok, String message) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MilvusStatus(boolean ok, String host, Integer port, String collectionName, Integer dimensions, String message) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LlmProviderStatus(String name, String model, boolean keyOk) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LlmStatus(LlmProviderStatus primary, LlmProviderStatus fallback) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmbeddingStatus(boolean ok, String model, Integer dimensions, String baseUrl) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DockerStatus(boolean ok, String version, boolean enabled, String image, String message) {}
}
