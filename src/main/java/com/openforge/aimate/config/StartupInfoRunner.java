package com.openforge.aimate.config;

import com.openforge.aimate.agent.DockerDetector;
import com.openforge.aimate.agent.ScriptDockerProperties;
import com.openforge.aimate.llm.LlmProperties;
import com.openforge.aimate.memory.EmbeddingProperties;
import com.openforge.aimate.memory.MilvusProperties;
import io.milvus.v2.client.MilvusClientV2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Prints a structured startup summary after the application context is fully ready.
 *
 * Checks performed:
 *   - MySQL: attempts to open a real JDBC connection and queries server version
 *   - Milvus: [OK]/[--] reflects actual connection (milvusClient bean non-null = connected)
 *   - LLM providers: primary + fallback config (API key is masked)
 *   - Embedding: model + dimensions
 *   - Runtime: Java version, virtual-thread status, server port
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupInfoRunner implements ApplicationRunner {

    private final DataSource            dataSource;
    private final LlmProperties         llmProperties;
    private final EmbeddingProperties   embeddingProperties;
    private final MilvusProperties      milvusProperties;
    @Nullable private final MilvusClientV2 milvusClient;
    private final Environment           env;
    private final DockerDetector        dockerDetector;
    private final ScriptDockerProperties scriptDockerProperties;

    private static final int LABEL_W = 10;
    private static final int VALUE_W = 58;

    /** 状态显示：用 [OK] / [--] 替代 ✓/✗，终端兼容且易扫读 */
    private static String status(boolean ok) {
        return ok ? "[OK]" : "[--]";
    }

    @Override
    public void run(ApplicationArguments args) {
        String dbStatus    = probeDatabaseShort();
        String port        = env.getProperty("server.port", "8080");
        String javaVersion = System.getProperty("java.version");
        boolean vtEnabled  = Boolean.parseBoolean(
                env.getProperty("spring.threads.virtual.enabled", "false"));
        boolean milvusConnected = (milvusClient != null);
        String pk          = maskKey(llmProperties.primary().apiKey());
        String fk          = maskKey(llmProperties.fallback().apiKey());

        StringBuilder b = new StringBuilder();
        b.append("\n  ┌─ AIMate — Startup Summary ─────────────────────────────────────────────\n");

        // Server
        b.append("  │  Server\n");
        b.append("  │    ").append(padR("port", LABEL_W)).append("  ").append(trunc(port, VALUE_W)).append("\n");
        b.append("  │    ").append(padR("java", LABEL_W)).append("  ").append(trunc(javaVersion, VALUE_W)).append("\n");
        b.append("  │    ").append(padR("virtual thread", LABEL_W)).append("  ").append(status(vtEnabled)).append("\n");

        // MySQL
        b.append("  ├──────────────────────────────────────────────────────────────────────────\n");
        b.append("  │  MySQL  ").append(trunc(dbStatus, VALUE_W + LABEL_W)).append("\n");

        // Milvus
        String milvusVal = status(milvusConnected) + "  "
                + milvusProperties.host() + ":" + milvusProperties.port()
                + "  " + milvusProperties.collectionName() + "  dim=" + milvusProperties.vectorDimensions()
                + (milvusConnected ? "" : "  (connection failed)");
        b.append("  ├──────────────────────────────────────────────────────────────────────────\n");
        b.append("  │  Milvus  ").append(trunc(milvusVal, VALUE_W + LABEL_W)).append("\n");

        // LLM
        String primaryVal = llmProperties.primary().name() + " [" + llmProperties.primary().model() + "]  key " + status(!"(not set)".equals(pk));
        String fallbackVal = llmProperties.fallback().name() + " [" + llmProperties.fallback().model() + "]  key " + status(!"(not set)".equals(fk));
        b.append("  ├──────────────────────────────────────────────────────────────────────────\n");
        b.append("  │  LLM\n");
        b.append("  │    ").append(padR("primary", LABEL_W)).append("  ").append(trunc(primaryVal, VALUE_W)).append("\n");
        b.append("  │    ").append(padR("fallback", LABEL_W)).append("  ").append(trunc(fallbackVal, VALUE_W)).append("\n");

        // Embedding
        String embedVal = embeddingProperties.model() + "  dim=" + embeddingProperties.dimensions() + "  " + embeddingProperties.baseUrl();
        b.append("  ├──────────────────────────────────────────────────────────────────────────\n");
        b.append("  │  Embedding  ").append(trunc(embedVal, VALUE_W + LABEL_W - 2)).append("\n");

        // Docker
        var dockerVer = dockerDetector.getDockerVersion();
        boolean dockerOn = scriptDockerProperties.enabled();
        String dockerVal = dockerVer.map(v -> status(true) + " v" + v + (dockerOn ? "  每用户一 Linux  image=" + scriptDockerProperties.image() : "")).orElse(status(false) + " 不可用" + (dockerOn ? "  (已配置启用，需安装并启动 Docker)" : ""));
        b.append("  ├──────────────────────────────────────────────────────────────────────────\n");
        b.append("  │  Docker  ").append(trunc(dockerVal, VALUE_W + LABEL_W)).append("\n");

        b.append("  └──────────────────────────────────────────────────────────────────────────\n");
        log.info("{}", b);
    }

    private static String padR(String s, int width) {
        if (s == null) s = "";
        return s.length() >= width ? s.substring(0, width) : s + " ".repeat(width - s.length());
    }

    private static String trunc(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 2) + "..";
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** One-line DB status for startup banner (full version, host/db may truncate if very long). */
    private String probeDatabaseShort() {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            String version = conn.getMetaData().getDatabaseProductVersion();
            String part = url.replaceFirst("jdbc:mysql://([^?]+).*", "$1");
            if (part.contains("@")) part = part.substring(part.indexOf('@') + 1);
            part = part.replaceAll("password=[^&]*", "***");
            return status(true) + " " + part + "  v" + version;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            int maxLen = 60;
            return status(false) + " " + (msg.length() > maxLen ? msg.substring(0, maxLen - 3) + "..." : msg);
        }
    }

    /**
     * Masks an API key: shows first 6 chars + "..." + last 4 chars.
     * Returns "(not set)" if the key looks like a placeholder.
     */
    private static String maskKey(String key) {
        if (key == null || key.isBlank() || key.startsWith("sk-placeholder")) {
            return "(not set)";
        }
        if (key.length() <= 10) return "***";
        return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
    }
}
