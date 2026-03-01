package com.openforge.aimate.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Detects whether Docker is available (daemon running, docker CLI in PATH).
 * Caches result for a short period to avoid running process on every request.
 */
@Slf4j
@Component
public class DockerDetector {

    private static final long CACHE_MS = 30_000; // 30 seconds
    private final AtomicReference<Optional<String>> cachedVersion = new AtomicReference<>(null);
    private final AtomicLong cachedAt = new AtomicLong(0);

    /**
     * Returns Docker server version if available, empty if not installed or not running.
     */
    public Optional<String> getDockerVersion() {
        long now = System.currentTimeMillis();
        if (cachedAt.get() != 0 && now - cachedAt.get() < CACHE_MS) {
            Optional<String> v = cachedVersion.get();
            if (v != null) return v;
        }
        Optional<String> result = detect();
        cachedVersion.set(result);
        cachedAt.set(now);
        return result;
    }

    public boolean isDockerAvailable() {
        return getDockerVersion().isPresent();
    }

    private static Optional<String> detect() {
        try {
            Process p = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
                    .redirectErrorStream(true)
                    .start();
            String out = readFully(p.getInputStream());
            int exit = p.waitFor();
            if (exit != 0 || out == null || out.isBlank()) return Optional.empty();
            return Optional.of(out.trim());
        } catch (Exception e) {
            log.debug("[DockerDetector] Docker not available: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String readFully(java.io.InputStream in) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
        } catch (Exception ignored) {
        }
        return sb.toString().trim();
    }

    /** Clear cache so next call will re-detect (e.g. after user installs Docker). */
    public void clearCache() {
        cachedVersion.set(null);
        cachedAt.set(0);
    }
}
