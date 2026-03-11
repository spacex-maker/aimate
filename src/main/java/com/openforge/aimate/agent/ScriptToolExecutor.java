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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Executes script tools (PYTHON_SCRIPT, NODE_SCRIPT, SHELL_CMD) by writing script to a temp file,
 * passing tool arguments as JSON on stdin, and capturing stdout as the result.
 * <p>
 * When the default interpreter is not found (e.g. "python" not in PATH on Windows), tries fallback
 * commands (e.g. "py", "python3") and/or the path from agent.script.python-path. On failure, returns
 * an error message that includes resolution advice (解决建议).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScriptToolExecutor {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_CHARS = 50_000;

    /** 系统级工具仅本机执行，不走 Docker。 */
    private static final Set<String> SYSTEM_TOOL_NAMES = Set.of("recall_memory", "store_memory", "tavily_search", "create_tool", "install_container_package", "run_container_cmd", "write_container_file");

    private final ScriptToolProperties scriptToolProperties;
    private final ScriptDockerProperties scriptDockerProperties;
    private final UserContainerManager userContainerManager;
    private final DockerScriptRunner dockerScriptRunner;

    /** Delegates to {@link #execute(AgentTool, String, Long, Consumer)} with null consumer. */
    public String execute(AgentTool tool, String argumentsJson, @Nullable Long userId) {
        return execute(tool, argumentsJson, userId, null);
    }

    /**
     * Execute the tool script with the given arguments JSON (from LLM tool call).
     * When agent.script.docker.enabled is true and userId is set, runs in the user's Docker container (created on first use, recycled after idle).
     * When outputChunkConsumer is non-null, container stdout/stderr are streamed to it in real time.
     */
    public String execute(AgentTool tool, String argumentsJson, @Nullable Long userId, @Nullable Consumer<String> outputChunkConsumer) {
        boolean isSystemTool = Boolean.TRUE.equals(tool.getIsSystem())
                || SYSTEM_TOOL_NAMES.contains(tool.getToolName());
        if (!isSystemTool && scriptDockerProperties.enabled() && userId != null) {
            String containerIdOrName = userContainerManager.getOrCreateContainer(userId);
            if (containerIdOrName != null) {
                if (!userContainerManager.ensureContainerRunning(containerIdOrName)) {
                    return "[ToolError] 用户容器已停止且无法重新启动，请刷新页面或联系管理员检查 Docker。";
                }
                log.debug("[ScriptTool] Executing in Docker container: {}", containerIdOrName);
                return dockerScriptRunner.runInContainer(containerIdOrName, tool, argumentsJson, outputChunkConsumer);
            }
            return "[ToolError] Docker 模式已开启但无法创建/获取用户容器。请确认 Docker 已启动且可访问（例如在终端执行 docker ps）。";
        }

        log.debug("[ScriptTool] Executing on host (Docker disabled, no userId, or system tool)");
        String content = tool.getScriptContent();
        if (content == null || content.isBlank()) {
            return errorWithAdvice("[ToolError] Script content is empty for tool: " + tool.getToolName(),
                    tool.getToolType(), false);
        }

        String ext = switch (tool.getToolType()) {
            case PYTHON_SCRIPT -> ".py";
            case NODE_SCRIPT -> ".js";
            case SHELL_CMD -> ".sh";
            default -> ".txt";
        };
        String baseName = tool.getEntryPoint() != null && !tool.getEntryPoint().isBlank()
                ? tool.getEntryPoint().replaceAll("[^a-zA-Z0-9._-]", "_")
                : tool.getToolName().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!baseName.endsWith(ext)) baseName += ext;

        Path scriptPath = null;
        try {
            scriptPath = Files.createTempFile("aimate_tool_", baseName);
            Files.writeString(scriptPath, content, StandardCharsets.UTF_8);

            List<List<String>> candidates = getCommandCandidates(tool.getToolType(), scriptPath);
            Process started = null;
            IOException lastIoException = null;
            for (List<String> command : candidates) {
                try {
                    ProcessBuilder pb = new ProcessBuilder(command)
                            .redirectErrorStream(false)
                            .directory(scriptPath.getParent().toFile());
                    started = pb.start();
                    break;
                } catch (IOException e) {
                    lastIoException = e;
                    log.debug("[ScriptTool] Command failed {}: {}", command, e.getMessage());
                }
            }
            if (started == null) {
                String msg = lastIoException != null ? lastIoException.getMessage() : "No interpreter available";
                return errorWithAdvice("[ToolError] Failed to run script: " + msg, tool.getToolType(), true);
            }
            final Process process = started;

            try (OutputStream stdin = process.getOutputStream()) {
                String input = (argumentsJson != null && !argumentsJson.isBlank()) ? argumentsJson : "{}";
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            }

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            Thread outReader = new Thread(() -> readStream(process.getInputStream(), stdout, MAX_OUTPUT_CHARS));
            Thread errReader = new Thread(() -> readStream(process.getErrorStream(), stderr, 2000));
            outReader.start();
            errReader.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            outReader.join(1000);
            errReader.join(1000);

            if (!finished) {
                process.destroyForcibly();
                return errorWithAdvice("[ToolError] Script timed out after " + TIMEOUT_SECONDS + " seconds. Reduce script work or increase timeout.",
                        tool.getToolType(), false);
            }

            if (process.exitValue() != 0) {
                String err = stderr.toString().trim();
                if (err.length() > 1500) err = err.substring(0, 1500) + "...";
                String msg = "[ToolError] Script exited with code " + process.exitValue()
                        + (err.isEmpty() ? "" : ": " + err);
                if (process.exitValue() == 127) {
                    String pkg = switch (tool.getToolType()) {
                        case NODE_SCRIPT -> "nodejs";
                        case SHELL_CMD -> "bash";
                        case PYTHON_SCRIPT, JAVA_NATIVE -> "python3";
                    };
                    msg += "\n\n[必须处理] 退出码 127 表示未找到解释器。若在 Docker 环境中，你必须先调用 install_container_package（packages=\"" + pkg + "\"），安装成功后再重新调用本脚本工具，不要直接向用户报错。";
                } else {
                    msg += "\n解决建议：检查脚本语法与依赖（如缺少 Python/Node 包），在本地用相同解释器运行脚本排查。";
                }
                return msg;
            }

            return stdout.toString().trim();
        } catch (IOException e) {
            log.warn("[ScriptTool] IO error executing {}: {}", tool.getToolName(), e.getMessage());
            return errorWithAdvice("[ToolError] Failed to run script: " + e.getMessage(), tool.getToolType(), isInterpreterNotFound(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[ToolError] Execution interrupted.";
        } finally {
            if (scriptPath != null) {
                try {
                    Files.deleteIfExists(scriptPath);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private boolean isInterpreterNotFound(IOException e) {
        if (e == null) return false;
        String msg = e.getMessage();
        return msg != null && (msg.contains("CreateProcess error=3") || msg.contains("Cannot run program") || msg.contains("找不到") || msg.contains("not found"));
    }

    private String errorWithAdvice(String error, AgentTool.ToolType type, boolean interpreterNotFound) {
        if (interpreterNotFound) {
            return error + "\n\n解决建议：\n" + resolutionAdvice(type);
        }
        return error;
    }

    private String resolutionAdvice(AgentTool.ToolType type) {
        return switch (type) {
            case PYTHON_SCRIPT -> "1) 安装 Python 并加入系统 PATH（Windows 安装时勾选 “Add Python to PATH”）；\n"
                    + "2) 或在 application.yml 中配置 agent.script.python-path 为可执行文件完整路径，例如：\n"
                    + "   agent:\n  script:\n    python-path: C:\\Python311\\python.exe";
            case NODE_SCRIPT -> "1) 安装 Node.js 并加入系统 PATH；\n"
                    + "2) 或配置 agent.script.node-path 为 node 可执行文件完整路径。";
            case SHELL_CMD -> "1) Linux/Mac 已自带 bash；Windows 请安装 Git for Windows（含 Git Bash）或 WSL；\n"
                    + "2) 或配置 agent.script.shell-path 为 bash 可执行文件完整路径。";
            default -> "请根据脚本类型安装对应解释器并加入 PATH，或在配置中指定解释器路径。";
        };
    }

    /** Returns list of command candidates (interpreter + script path). First that can start is used. */
    private List<List<String>> getCommandCandidates(AgentTool.ToolType type, Path scriptPath) {
        String path = scriptPath.toAbsolutePath().toString();
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("windows");
        List<List<String>> out = new ArrayList<>();

        switch (type) {
            case PYTHON_SCRIPT -> {
                String configured = scriptToolProperties.getPythonPathOrNull();
                if (configured != null) out.add(List.of(configured, path));
                if (isWindows) {
                    out.add(List.of("python", path));
                    out.add(List.of("py", path));   // Windows Python launcher
                    out.add(List.of("python3", path));
                } else {
                    out.add(List.of("python3", path));
                    out.add(List.of("python", path));
                }
            }
            case NODE_SCRIPT -> {
                String configured = scriptToolProperties.getNodePathOrNull();
                if (configured != null) out.add(List.of(configured, path));
                out.add(List.of("node", path));
            }
            case SHELL_CMD -> {
                String configured = scriptToolProperties.getShellPathOrNull();
                if (configured != null) out.add(List.of(configured, path));
                out.add(List.of("bash", path));
                if (!isWindows) out.add(List.of("sh", path));
            }
            default -> out.add(List.of("python3", path));
        }
        return out;
    }

    private static void readStream(InputStream in, StringBuilder out, int maxChars) {
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buf = new char[1024];
            int n;
            while ((n = r.read(buf)) != -1 && out.length() < maxChars) {
                out.append(buf, 0, n);
            }
            if (out.length() > maxChars) {
                out.setLength(maxChars);
                out.append("\n... (output truncated)");
            }
        } catch (IOException ignored) {
        }
    }
}
