package com.openforge.aimate.config;

import com.openforge.aimate.agent.dto.ComponentStatusDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

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

    private static final int LABEL_W = 10;
    private static final int VALUE_W = 58;

    private final ComponentStatusService componentStatusService;

    /** 状态显示：用 [OK] / [--] 替代 ✓/✗，终端兼容且易扫读 */
    private static String status(boolean ok) {
        return ok ? "[OK]" : "[--]";
    }

    @Override
    public void run(ApplicationArguments args) {
        ComponentStatusDto dto = componentStatusService.getStatus();

        StringBuilder b = new StringBuilder();
        b.append("\n  ┌─ AIMate — Startup Summary ─────────────────────────────────────────────\n");

        var s = dto.server();
        b.append("  │  Server\n");
        b.append("  │    ").append(padR("port", LABEL_W)).append("  ").append(trunc(s.port(), VALUE_W)).append("\n");
        b.append("  │    ").append(padR("java", LABEL_W)).append("  ").append(trunc(s.javaVersion(), VALUE_W)).append("\n");
        b.append("  │    ").append(padR("virtual thread", LABEL_W)).append("  ").append(status(s.virtualThreadEnabled())).append("\n");

        b.append("  ├──────────────────────────────────────────────────────────────────────────\n");
        b.append("  │  MySQL  ").append(trunc(status(dto.mysql().ok()) + "  " + dto.mysql().message(), VALUE_W + LABEL_W)).append("\n");

        var mv = dto.milvus();
        String milvusVal = status(mv.ok()) + "  " + mv.host() + ":" + mv.port() + "  " + mv.collectionName() + "  dim=" + mv.dimensions() + (mv.message() != null ? "  (" + mv.message() + ")" : "");
        b.append("  ├──────────────────────────────────────────────────────────────────────────\n");
        b.append("  │  Milvus  ").append(trunc(milvusVal, VALUE_W + LABEL_W)).append("\n");

        var llm = dto.llm();
        String primaryVal = llm.primary().name() + " [" + llm.primary().model() + "]  key " + status(llm.primary().keyOk());
        String fallbackVal = llm.fallback().name() + " [" + llm.fallback().model() + "]  key " + status(llm.fallback().keyOk());
        b.append("  ├──────────────────────────────────────────────────────────────────────────\n");
        b.append("  │  LLM\n");
        b.append("  │    ").append(padR("primary", LABEL_W)).append("  ").append(trunc(primaryVal, VALUE_W)).append("\n");
        b.append("  │    ").append(padR("fallback", LABEL_W)).append("  ").append(trunc(fallbackVal, VALUE_W)).append("\n");

        var emb = dto.embedding();
        String embedVal = status(emb.ok()) + "  " + emb.model() + "  dim=" + emb.dimensions() + "  " + emb.baseUrl();
        b.append("  ├──────────────────────────────────────────────────────────────────────────\n");
        b.append("  │  Embedding  ").append(trunc(embedVal, VALUE_W + LABEL_W - 2)).append("\n");

        var dr = dto.docker();
        String dockerVal = status(dr.ok()) + (dr.version() != null ? " v" + dr.version() : "") + (dr.enabled() ? "  每用户一 Linux  image=" + (dr.image() != null ? dr.image() : "") : "") + (dr.message() != null ? "  " + dr.message() : "");
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
}
