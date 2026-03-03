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
 *   - Milvus: ✓/✗ reflects actual connection (milvusClient bean non-null = connected)
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

    private static final int W = 84;
    private static final int LABEL_W = 14;

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

        String sep = "═".repeat(W);

        StringBuilder b = new StringBuilder();
        b.append("\n");
        b.append("╔").append(sep).append("╗\n");
        b.append("║").append(center("AIMate — Startup Summary", W)).append("║\n");
        b.append("╠").append(sep).append("╣\n");

        // Server
        b.append("║  Server").append(" ".repeat(W - 8)).append("║\n");
        b.append("║").append(row("port", port)).append("║\n");
        b.append("║").append(row("java", javaVersion)).append("║\n");
        b.append("║").append(row("virtual threads", vtEnabled ? "✓" : "✗")).append("║\n");

        // MySQL
        b.append("╠").append(sep).append("╣\n");
        b.append("║  MySQL").append(" ".repeat(W - 7)).append("║\n");
        b.append("║").append(row("", dbStatus)).append("║\n");

        // Milvus（✓/✗ 以实际连接为准，连接失败时 bean 为 null）
        b.append("╠").append(sep).append("╣\n");
        String milvusVal = (milvusConnected ? "✓" : "✗") + "  "
                + milvusProperties.host() + ":" + milvusProperties.port()
                + "  " + milvusProperties.collectionName() + "  dim=" + milvusProperties.vectorDimensions()
                + (milvusConnected ? "" : "  (connection failed)");
        b.append("║  Milvus").append(" ".repeat(W - 8)).append("║\n");
        b.append("║").append(row("", milvusVal)).append("║\n");

        // LLM
        b.append("╠").append(sep).append("╣\n");
        b.append("║  LLM").append(" ".repeat(W - 5)).append("║\n");
        b.append("║").append(row("primary", llmProperties.primary().name() + " [" + llmProperties.primary().model() + "]  key " + ("(not set)".equals(pk) ? "✗" : "✓"))).append("║\n");
        b.append("║").append(row("fallback", llmProperties.fallback().name() + " [" + llmProperties.fallback().model() + "]  key " + ("(not set)".equals(fk) ? "✗" : "✓"))).append("║\n");

        // Embedding
        b.append("╠").append(sep).append("╣\n");
        String embedVal = embeddingProperties.model() + "  dim=" + embeddingProperties.dimensions() + "  " + embeddingProperties.baseUrl();
        b.append("║  Embedding").append(" ".repeat(W - 11)).append("║\n");
        b.append("║").append(row("", embedVal)).append("║\n");

        // Docker（每用户一个独立 Linux 容器，隔离执行任意命令）
        b.append("╠").append(sep).append("╣\n");
        var dockerVer = dockerDetector.getDockerVersion();
        boolean dockerOn = scriptDockerProperties.enabled();
        String dockerVal = dockerVer.map(v -> "✓ v" + v + (dockerOn ? "  每用户一 Linux  image=" + scriptDockerProperties.image() : "")).orElse("✗ 不可用" + (dockerOn ? "  (已配置启用，需安装并启动 Docker)" : ""));
        b.append("║  Docker").append(" ".repeat(W - 8)).append("║\n");
        b.append("║").append(row("", dockerVal)).append("║\n");

        b.append("╚").append(sep).append("╝\n");
        log.info("{}", b);
    }

    /** One row: "    label    value" with fixed label width; value may wrap or truncate to fit. */
    private static String row(String label, String value) {
        String l = padR(label == null ? "" : label, LABEL_W);
        int valLen = W - 4 - LABEL_W - 2;
        String v = value == null ? "" : value;
        if (v.length() > valLen) v = v.substring(0, valLen - 2) + "..";
        return "    " + l + "  " + padR(v, valLen);
    }

    private static String padR(String s, int width) {
        if (s == null) s = "";
        return s.length() >= width ? s.substring(0, width) : s + " ".repeat(width - s.length());
    }

    private static String center(String s, int width) {
        if (s == null) s = "";
        int len = Math.min(s.length(), width);
        int left = (width - len) / 2;
        int right = width - len - left;
        return " ".repeat(left) + (len == s.length() ? s : s.substring(0, len)) + " ".repeat(right);
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
            return "✔ " + part + "  v" + version;
        } catch (Exception e) {
            String msg = e.getMessage();
            return "✘ " + (msg != null && msg.length() > W - 6 ? msg.substring(0, W - 9) + "..." : msg);
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
