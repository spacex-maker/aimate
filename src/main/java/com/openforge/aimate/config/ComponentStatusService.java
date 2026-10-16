package com.openforge.aimate.config;

import com.openforge.aimate.agent.DockerDetector;
import com.openforge.aimate.agent.ScriptDockerProperties;
import com.openforge.aimate.agent.dto.ComponentStatusDto;
import com.openforge.aimate.llm.LlmProperties;
import com.openforge.aimate.memory.TencentVectorStore;
import com.openforge.aimate.memory.VectorDbProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 采集各组件（MySQL、腾讯云向量库、LLM、向量模型、Docker）连接/配置状态，
 * 供启动摘要日志与管理端 GET /api/admin/component-status 使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentStatusService {

    private final DataSource dataSource;
    private final Environment env;
    private final TencentVectorStore vectorStore;
    private final VectorDbProperties vectorDbProperties;
    private final LlmProperties llmProperties;
    private final DockerDetector dockerDetector;
    private final ScriptDockerProperties scriptDockerProperties;

    public ComponentStatusDto getStatus() {
        var mysql = probeMysql();
        var milvus = probeMilvus();
        var embedding = probeEmbedding();
        var docker = probeDocker();

        return new ComponentStatusDto(
                new ComponentStatusDto.ServerStatus(
                        env.getProperty("server.port", "8080"),
                        System.getProperty("java.version"),
                        Boolean.parseBoolean(env.getProperty("spring.threads.virtual.enabled", "false"))
                ),
                mysql,
                milvus,
                new ComponentStatusDto.LlmStatus(
                        new ComponentStatusDto.LlmProviderStatus(
                                llmProperties.primary().name(),
                                llmProperties.primary().model(),
                                isKeySet(llmProperties.primary().apiKey())
                        ),
                        new ComponentStatusDto.LlmProviderStatus(
                                llmProperties.fallback().name(),
                                llmProperties.fallback().model(),
                                isKeySet(llmProperties.fallback().apiKey())
                        )
                ),
                embedding,
                docker
        );
    }

    private static boolean isKeySet(String key) {
        return key != null && !key.isBlank() && !key.startsWith("sk-placeholder");
    }

    private ComponentStatusDto.MysqlStatus probeMysql() {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            String version = conn.getMetaData().getDatabaseProductVersion();
            String part = url.replaceFirst("jdbc:mysql://([^?]+).*", "$1");
            if (part.contains("@")) part = part.substring(part.indexOf('@') + 1);
            part = part.replaceAll("password=[^&]*", "***");
            return new ComponentStatusDto.MysqlStatus(true, part + "  v" + version);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.length() > 120) msg = msg.substring(0, 117) + "...";
            return new ComponentStatusDto.MysqlStatus(false, msg);
        }
    }

    private ComponentStatusDto.MilvusStatus probeMilvus() {
        boolean ok = vectorStore.available();
        String message = ok
                ? vectorDbProperties.embeddingModel()
                : "未连接。设置 TENCENT_VECTORDB_URL 与 TENCENT_VECTORDB_KEY";
        return new ComponentStatusDto.MilvusStatus(
                ok,
                vectorDbProperties.url() == null ? "" : vectorDbProperties.url(),
                vectorDbProperties.timeoutSeconds(),
                vectorDbProperties.memoryCollection(),
                vectorDbProperties.vectorDimensions(),
                message
        );
    }

    private ComponentStatusDto.EmbeddingStatus probeEmbedding() {
        boolean ok = vectorStore.available();
        return new ComponentStatusDto.EmbeddingStatus(
                ok,
                vectorDbProperties.embeddingModel(),
                vectorDbProperties.vectorDimensions(),
                "tencent-vectordb"
        );
    }

    private ComponentStatusDto.DockerStatus probeDocker() {
        var verOpt = dockerDetector.getDockerVersion();
        boolean enabled = scriptDockerProperties.enabled();
        if (verOpt.isPresent()) {
            return new ComponentStatusDto.DockerStatus(
                    true,
                    verOpt.get(),
                    enabled,
                    scriptDockerProperties.image(),
                    null
            );
        }
        return new ComponentStatusDto.DockerStatus(
                false,
                null,
                enabled,
                enabled ? scriptDockerProperties.image() : null,
                enabled ? "已配置启用，需安装并启动 Docker" : "不可用"
        );
    }
}
