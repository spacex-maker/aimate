package com.openforge.aimate.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每用户一个独立 Linux 容器：创建/复用、空闲回收。
 * 使用完整 Linux 基础镜像，用户脚本在该隔离环境中执行，安全可执行任意命令。
 */
@Slf4j
@Component
public class UserContainerManager {

    private static final String CONTAINER_NAME_PREFIX = "aimate-user-";

    private final ScriptDockerProperties scriptDockerProperties;
    private final Map<Long, ContainerInfo> userContainers = new ConcurrentHashMap<>();

    public UserContainerManager(ScriptDockerProperties scriptDockerProperties) {
        this.scriptDockerProperties = scriptDockerProperties;
    }

    /**
     * Only reads state: if this user has a running container, returns its name. Does not create.
     */
    public Optional<String> getExistingRunningContainerName(Long userId) {
        if (userId == null || !scriptDockerProperties.enabled()) return Optional.empty();
        ContainerInfo info = userContainers.get(userId);
        if (info != null && isContainerRunning(info.containerId)) return Optional.of(info.containerId);
        return Optional.empty();
    }

    /**
     * Returns the container ID for this user, creating and starting the container if needed.
     * Updates last-used timestamp. Returns null if Docker is disabled or creation fails.
     */
    @Nullable
    public String getOrCreateContainer(Long userId) {
        if (userId == null || !scriptDockerProperties.enabled()) return null;
        ContainerInfo info = userContainers.get(userId);
        if (info != null && isContainerRunning(info.containerId)) {
            info.setLastUsedAt(System.currentTimeMillis());
            return info.containerId;
        }
        if (info != null) {
            userContainers.remove(userId);
        }
        String name = CONTAINER_NAME_PREFIX + userId;
        String wantedImage = scriptDockerProperties.image();
        // 先看是否已有同名容器（例如上次进程未回收或重启后 map 清空）
        // 若已有容器但镜像与配置不一致（如从 python:3.11-slim 改为 debian:bookworm-slim），则删除旧容器并用新镜像创建
        String existing = useExistingContainerIfPresent(name, userId, wantedImage);
        if (existing != null) return existing;

        String image = scriptDockerProperties.image();
        List<String> run = buildDockerRunCommand(name, image);
        try {
            Process p = new ProcessBuilder(run).redirectErrorStream(true).start();
            String out = readFully(p.getInputStream());
            int exit = p.waitFor();
            if (exit != 0) {
                if (out != null && out.contains("already in use") && out.contains(name)) {
                    String reused = useExistingContainerIfPresent(name, userId, wantedImage);
                    if (reused != null) return reused;
                }
                log.warn("[UserContainer] docker run failed for user {}: {}", userId, out);
                return null;
            }
            userContainers.put(userId, new ContainerInfo(name, System.currentTimeMillis()));
            log.info("[UserContainer] Created container for user {}: {}", userId, name);
            return name;
        } catch (Exception e) {
            log.warn("[UserContainer] Failed to create container for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 若已存在同名容器且镜像与配置一致：在运行则加入 map 并返回名称；已停止则 start 后加入 map 并返回。
     * 若镜像与配置不一致（如从 python:3.11-slim 改为 debian:bookworm-slim），则删除旧容器并返回 null，由调用方用新镜像创建。
     */
    @Nullable
    private String useExistingContainerIfPresent(String name, Long userId, String wantedImage) {
        if (!containerExists(name)) return null;
        String currentImage = getContainerImage(name);
        if (currentImage != null && !normalizeImageRef(currentImage).equals(normalizeImageRef(wantedImage))) {
            log.info("[UserContainer] Existing container {} has image {} but config wants {}, removing and will recreate.", name, currentImage, wantedImage);
            removeContainer(name);
            return null;
        }
        // 旧版本曾用 --network none 创建容器，无网络则无法 apt-get，需删掉重建
        if ("none".equalsIgnoreCase(getContainerNetworkMode(name))) {
            log.info("[UserContainer] Existing container {} has network=none (no DNS), removing so apt-get can work.", name);
            removeContainer(name);
            return null;
        }
        if (!isContainerRunning(name)) {
            try {
                runDocker("start", name);
            } catch (Exception e) {
                log.warn("[UserContainer] docker start failed for {}: {}", name, e.getMessage());
                return null;
            }
        }
        userContainers.put(userId, new ContainerInfo(name, System.currentTimeMillis()));
        log.info("[UserContainer] Reusing existing container for user {}: {}", userId, name);
        return name;
    }

    /** docker inspect --format '{{.Config.Image}}' 返回的镜像名，可能带 digest 或 tag，需归一化后比较 */
    @Nullable
    private String getContainerImage(String name) {
        try {
            Process p = new ProcessBuilder("docker", "inspect", "--format", "{{.Config.Image}}", name)
                    .redirectErrorStream(true).start();
            String out = readFully(p.getInputStream());
            p.waitFor();
            return (out != null && !out.isBlank()) ? out.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private String getContainerNetworkMode(String name) {
        try {
            Process p = new ProcessBuilder("docker", "inspect", "--format", "{{.HostConfig.NetworkMode}}", name)
                    .redirectErrorStream(true).start();
            String out = readFully(p.getInputStream());
            p.waitFor();
            return (out != null && !out.isBlank()) ? out.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void removeContainer(String name) {
        try {
            if (isContainerRunning(name)) runDocker("stop", name);
            runDocker("rm", name);
        } catch (Exception e) {
            log.warn("[UserContainer] Failed to remove container {}: {}", name, e.getMessage());
        }
    }

    private static String normalizeImageRef(String ref) {
        if (ref == null || ref.isBlank()) return "";
        String s = ref.trim();
        int at = s.indexOf('@');
        if (at > 0) s = s.substring(0, at);
        return s;
    }

    private boolean containerExists(String name) {
        try {
            Process p = new ProcessBuilder("docker", "ps", "-a", "-q", "-f", "name=" + name)
                    .redirectErrorStream(true).start();
            String out = readFully(p.getInputStream());
            p.waitFor();
            return out != null && !out.trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Scheduled(fixedDelay = 300_000) // 5 min
    public void recycleIdleContainers() {
        if (!scriptDockerProperties.enabled()) return;
        long idleMs = scriptDockerProperties.idleMinutes() * 60L * 1000L;
        long now = System.currentTimeMillis();
        userContainers.entrySet().removeIf(entry -> {
            Long userId = entry.getKey();
            ContainerInfo info = entry.getValue();
            if (now - info.getLastUsedAt() < idleMs) return false;
            if (!isContainerRunning(info.containerId)) {
                return true;
            }
            try {
                runDocker("stop", info.containerId);
                runDocker("rm", info.containerId);
                log.info("[UserContainer] Recycled idle container for user {}", userId);
            } catch (Exception e) {
                log.warn("[UserContainer] Recycle failed for user {}: {}", userId, e.getMessage());
            }
            return true;
        });
    }

    private boolean isContainerRunning(String containerIdOrName) {
        try {
            Process p = new ProcessBuilder("docker", "ps", "-q", "-f", "name=" + containerIdOrName)
                    .redirectErrorStream(true).start();
            String out = readFully(p.getInputStream());
            p.waitFor();
            return out != null && !out.trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** 按配置组装 docker run：资源限制、只读根盘+tmpfs、非 root 用户等标准选项 */
    private List<String> buildDockerRunCommand(String containerName, String image) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("-d");
        // 使用默认网络，以便 AI 通过 install_container_package 在容器内执行 apt-get install
        if (scriptDockerProperties.isMemoryLimitSet()) {
            cmd.add("--memory");
            cmd.add(scriptDockerProperties.memoryLimit().trim());
        }
        if (scriptDockerProperties.isCpuLimitSet()) {
            cmd.add("--cpus");
            cmd.add(String.valueOf(scriptDockerProperties.cpuLimit()));
        }
        if (scriptDockerProperties.readOnlyRootfs()) {
            cmd.add("--read-only");
            cmd.add("--tmpfs");
            cmd.add("/tmp:rw,nosuid,size=64m");
        }
        if (scriptDockerProperties.isRunAsUserSet()) {
            cmd.add("--user");
            cmd.add(scriptDockerProperties.runAsUser().trim());
        }
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add(image);
        cmd.add("sleep");
        cmd.add("infinity");
        return cmd;
    }

    private void runDocker(String cmd, String arg) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("docker", cmd, arg).redirectErrorStream(true).start();
        readFully(p.getInputStream());
        if (p.waitFor() != 0) throw new IOException("docker " + cmd + " failed");
    }

    private static String readFully(java.io.InputStream in) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
        } catch (IOException ignored) {
        }
        return sb.toString().trim();
    }

    private static class ContainerInfo {
        private final String containerId;
        private volatile long lastUsedAt;

        ContainerInfo(String containerId, long lastUsedAt) {
            this.containerId = containerId;
            this.lastUsedAt = lastUsedAt;
        }

        void setLastUsedAt(long lastUsedAt) { this.lastUsedAt = lastUsedAt; }
        long getLastUsedAt() { return lastUsedAt; }
    }
}
