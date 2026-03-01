package com.openforge.aimate.agent;

import com.openforge.aimate.domain.AgentTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 在用户专属 Linux 容器内执行脚本：拷贝到容器后直接执行该文件，不指定解释器。
 * 由脚本首行 shebang（#!/usr/bin/env python3 等）或系统约定决定用谁跑；系统只提供「在 Linux 下执行」能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerScriptRunner {

    private final ScriptDockerProperties scriptDockerProperties;
    private static final int MAX_OUTPUT_CHARS = 50_000;
    private static final String CONTAINER_SCRIPT_PATH = "/tmp/aimate_script";

    /**
     * Execute the tool script inside the given container. When outputChunkConsumer is non-null, stdout/stderr
     * are streamed to it in real time. Uses idle timeout (no output for N seconds = stuck) and max total timeout.
     */
    public String runInContainer(String containerIdOrName, AgentTool tool, String argumentsJson,
                                 @Nullable Consumer<String> outputChunkConsumer) {
        String content = tool.getScriptContent();
        if (content == null || content.isBlank()) {
            return "[ToolError] Script content is empty for tool: " + tool.getToolName();
        }

        String remotePath = CONTAINER_SCRIPT_PATH;
        Path scriptPath = null;
        try {
            scriptPath = Files.createTempFile("aimate_docker_", "");
            Files.writeString(scriptPath, content, StandardCharsets.UTF_8);

            String localPath = scriptPath.toAbsolutePath().toString();
            ProcessBuilder cpPb = new ProcessBuilder("docker", "cp", localPath, containerIdOrName + ":" + remotePath);
            Process cpProcess = cpPb.start();
            String cpErr = readFully(cpProcess.getErrorStream());
            if (cpProcess.waitFor() != 0) {
                return "[ToolError] Failed to copy script into container: " + cpErr;
            }

            ProcessBuilder chmodPb = new ProcessBuilder("docker", "exec", containerIdOrName, "chmod", "+x", remotePath);
            Process chmodProcess = chmodPb.start();
            if (chmodProcess.waitFor() != 0) {
                return "[ToolError] Failed to chmod +x script in container.";
            }

            // 直接执行该文件，不指定解释器；由脚本 shebang 或系统决定
            // PYTHONUNBUFFERED=1 避免 Python 在非 TTY 下全缓冲 stdout，否则 print() 要等脚本结束或缓冲区满才有输出
            List<String> execCmd = List.of("docker", "exec", "-i", "-e", "PYTHONUNBUFFERED=1", containerIdOrName, remotePath);
            ProcessBuilder execPb = new ProcessBuilder(execCmd)
                    .redirectErrorStream(false);
            Process execProcess = execPb.start();

            try (OutputStream stdin = execProcess.getOutputStream()) {
                String input = (argumentsJson != null && !argumentsJson.isBlank()) ? argumentsJson : "{}";
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            }

            int idleSec = scriptDockerProperties.scriptIdleTimeoutSeconds();
            long idleMs = idleSec * 1000L;
            AtomicLong lastOutputTime = new AtomicLong(System.currentTimeMillis());
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread outReader = new Thread(() -> readStreamWithCallback(
                    execProcess.getInputStream(), stdout, MAX_OUTPUT_CHARS, lastOutputTime,
                    outputChunkConsumer));
            Thread errReader = new Thread(() -> readStreamWithCallback(
                    execProcess.getErrorStream(), stderr, 2000, lastOutputTime,
                    outputChunkConsumer != null ? line -> outputChunkConsumer.accept("[stderr] " + line + "\n") : null));
            outReader.start();
            errReader.start();

            // 不设总时长：仅当连续无输出超过 idleSec 才视为卡住；有输出（如长时间下载）会一直跑
            while (execProcess.isAlive() && !execProcess.waitFor(500, TimeUnit.MILLISECONDS)) {
                if (execProcess.isAlive()) {
                    long now = System.currentTimeMillis();
                    if (now - lastOutputTime.get() >= idleMs) {
                        execProcess.destroyForcibly();
                        try { outReader.join(2000); errReader.join(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                        return "[ToolError] No output for " + idleSec + "s, considered stuck. Last output:\n" + truncate(stdout.toString(), 800);
                    }
                }
            }
            try { outReader.join(2000); errReader.join(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

            if (execProcess.exitValue() != 0) {
                String err = stderr.toString().trim();
                if (err.length() > 1500) err = err.substring(0, 1500) + "...";
                String msg = "[ToolError] Script exited with code " + execProcess.exitValue() + (err.isEmpty() ? "" : ": " + err);
                if (execProcess.exitValue() == 127) {
                    msg += "\n\n[必须处理] 退出码 127 表示容器内未安装脚本 shebang 指定的解释器。请先调用 install_container_package 安装对应运行时后重试。";
                }
                return msg;
            }

            return stdout.toString().trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[ToolError] Execution interrupted.";
        } catch (IOException e) {
            log.warn("[DockerScript] IO error: {}", e.getMessage());
            return "[ToolError] " + e.getMessage();
        } finally {
            if (scriptPath != null) {
                try { Files.deleteIfExists(scriptPath); } catch (IOException ignored) {}
            }
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s == null ? "" : s;
        return s.substring(0, maxLen) + "...";
    }

    private static void readStreamWithCallback(InputStream in, StringBuilder out, int maxChars,
                                               AtomicLong lastOutputTime, Consumer<String> onChunk) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null && out.length() < maxChars) {
                out.append(line).append("\n");
                lastOutputTime.set(System.currentTimeMillis());
                if (onChunk != null) {
                    try { onChunk.accept(line + "\n"); } catch (Exception ignored) {}
                }
            }
            if (out.length() >= maxChars) out.append("\n... (truncated)");
        } catch (IOException ignored) {}
    }

    private static String readFully(InputStream in) {
        StringBuilder sb = new StringBuilder();
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buf = new char[1024];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
        } catch (IOException ignored) {
        }
        return sb.toString().trim();
    }

    private static void readStream(InputStream in, StringBuilder out, int maxChars) {
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buf = new char[1024];
            int n;
            while ((n = r.read(buf)) != -1 && out.length() < maxChars) out.append(buf, 0, n);
            if (out.length() > maxChars) {
                out.setLength(maxChars);
                out.append("\n... (truncated)");
            }
        } catch (IOException ignored) {
        }
    }
}
