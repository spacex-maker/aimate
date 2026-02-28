package com.openforge.aimate.config;

import com.openforge.aimate.llm.LlmProperties;
import com.openforge.aimate.memory.EmbeddingProperties;
import com.openforge.aimate.memory.MilvusProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Prints a structured startup summary after the application context is fully ready.
 *
 * Checks performed:
 *   - MySQL: attempts to open a real JDBC connection and queries server version
 *   - Milvus: reported by MilvusConfig at bean creation; we only echo the address here
 *   - LLM providers: primary + fallback config (API key is masked)
 *   - Embedding: model + dimensions
 *   - Runtime: Java version, virtual-thread status, server port
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupInfoRunner implements ApplicationRunner {

    private final DataSource       dataSource;
    private final LlmProperties    llmProperties;
    private final EmbeddingProperties embeddingProperties;
    private final MilvusProperties milvusProperties;
    private final Environment      env;

    @Override
    public void run(ApplicationArguments args) {
        String dbStatus    = probeDatabase();
        String port        = env.getProperty("server.port", "8080");
        String javaVersion = System.getProperty("java.version");
        boolean vtEnabled  = Boolean.parseBoolean(
                env.getProperty("spring.threads.virtual.enabled", "false"));

        String milvusEnabled = env.getProperty("agent.milvus.enabled", "true");

        log.info("""

                ╔══════════════════════════════════════════════════════════╗
                ║              AIMate  —  Startup Summary                  ║
                ╠══════════════════════════════════════════════════════════╣
                ║  Server                                                  ║
                ║    HTTP Port      : {}
                ║    Java Version   : {}
                ║    Virtual Threads: {}
                ╠══════════════════════════════════════════════════════════╣
                ║  Database (MySQL)                                        ║
                ║    {}
                ╠══════════════════════════════════════════════════════════╣
                ║  Vector DB (Milvus)                                      ║
                ║    Enabled        : {}
                ║    Address        : {}:{}
                ║    Collection     : {}  dim={}
                ╠══════════════════════════════════════════════════════════╣
                ║  LLM Providers                                           ║
                ║    Primary        : {}  [{}]  key={}
                ║    Fallback       : {}  [{}]  key={}
                ╠══════════════════════════════════════════════════════════╣
                ║  Embedding                                               ║
                ║    Model          : {}  dim={}
                ║    Endpoint       : {}
                ╚══════════════════════════════════════════════════════════╝
                """,
                port,
                javaVersion,
                vtEnabled ? "✔ enabled" : "✘ disabled",

                dbStatus,

                milvusEnabled,
                milvusProperties.host(), milvusProperties.port(),
                milvusProperties.collectionName(), milvusProperties.vectorDimensions(),

                llmProperties.primary().name(),
                llmProperties.primary().model(),
                maskKey(llmProperties.primary().apiKey()),

                llmProperties.fallback().name(),
                llmProperties.fallback().model(),
                maskKey(llmProperties.fallback().apiKey()),

                embeddingProperties.model(),
                embeddingProperties.dimensions(),
                embeddingProperties.baseUrl()
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Opens a real JDBC connection and reads the DB server version.
     * Returns a one-line summary or error message.
     */
    private String probeDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            String url     = conn.getMetaData().getURL();
            String version = conn.getMetaData().getDatabaseProductVersion();
            // Strip credentials from the JDBC URL for safe logging
            String safeUrl = url.replaceAll("password=[^&;]*", "password=***");
            return "✔ Connected  version=" + version + "  url=" + safeUrl;
        } catch (Exception e) {
            return "✘ FAILED — " + e.getMessage();
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
