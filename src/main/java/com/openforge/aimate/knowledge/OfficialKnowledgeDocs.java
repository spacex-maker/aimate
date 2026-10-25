package com.openforge.aimate.knowledge;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 官方知识库正文：从 classpath {@code official-knowledge/{code}.md} 加载。
 */
final class OfficialKnowledgeDocs {

    private OfficialKnowledgeDocs() {}

    static String load(String code) {
        String path = "official-knowledge/" + code + ".md";
        ClassPathResource res = new ClassPathResource(path);
        if (!res.exists()) {
            throw new IllegalStateException("missing official knowledge doc: " + path);
        }
        try (InputStream in = res.getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("failed to read " + path, e);
        }
    }

    /** 已有正文过短或明显短于种子时，用种子覆盖升级。 */
    static boolean shouldEnrich(String existing, String seed) {
        if (seed == null || seed.isBlank()) return false;
        if (existing == null || existing.isBlank()) return true;
        int e = existing.trim().length();
        int s = seed.trim().length();
        if (e < 200) return true;
        return e < (int) (s * 0.6);
    }
}
