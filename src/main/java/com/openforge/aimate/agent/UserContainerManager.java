package com.openforge.aimate.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
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
     * 仅查询容器状态，不创建、不启动。用于 script-status 等只读场景，避免「关闭后刷新状态」时误把容器拉起来。
     * @return RUNNING(容器名) / STOPPED(容器名) / NONE(null)，第二个为容器名（存在时）
     */
    public ContainerStatusReadOnly getContainerStatusReadOnly(Long userId) {
        if (userId == null || !scriptDockerProperties.enabled()) {
            return new ContainerStatusReadOnly(ContainerState.NONE, null);
        }
        String name = CONTAINER_NAME_PREFIX + userId;
        ContainerInfo info = userContainers.get(userId);
        if (info != null && isContainerRunning(info.containerId)) {
            return new ContainerStatusReadOnly(ContainerState.RUNNING, info.containerId);
        }
        if (!containerExists(name)) {
            return new ContainerStatusReadOnly(ContainerState.NONE, null);
        }
        return new ContainerStatusReadOnly(isContainerRunning(name) ? ContainerState.RUNNING : ContainerState.STOPPED, name);
    }

    public enum ContainerState { RUNNING, STOPPED, NONE }

    public record ContainerStatusReadOnly(ContainerState state, @Nullable String containerName) {}

    /**
     * Returns the container ID for this user, creating and starting the container if needed.
     * Updates last-used timestamp. Returns null if Docker is disabled or creation fails.
     */
    @Nullable
    public String getOrCreateContainer(Long userId) {
        if (userId == null) {
            log.warn("[UserContainer] getOrCreateContainer skipped: userId is null (未登录或认证未注入 userId)");
            return null;
        }
        if (!scriptDockerProperties.enabled()) {
            log.warn("[UserContainer] getOrCreateContainer skipped: script Docker is disabled (agent.script.docker.enabled=false)");
            return null;
        }
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
        String existing = useExistingContainerIfPresent(name, userId, wantedImage);
        if (existing != null) return existing;

        log.info("[UserContainer] No existing container for user {}, creating with image={}", userId, wantedImage);
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
                log.warn("[UserContainer] docker run failed for user {} (exit={}): {}", userId, exit, out);
                return null;
            }
            userContainers.put(userId, new ContainerInfo(name, System.currentTimeMillis()));
            log.info("[UserContainer] Created container for user {}: {}", userId, name);
            return name;
        } catch (Exception e) {
            log.warn("[UserContainer] Failed to create container for user {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 若已存在同名容器且镜像与配置一致：在运行则加入 map 并返回名称；已停止则 start 后加入 map 并返回。
     * 若镜像与配置不一致（如从 python:3.11-slim 改为 debian:bookworm-slim），则删除旧容器并返回 null，由调用方用新镜像创建。
     */
    @Nullable
    private String useExistingContainerIfPresent(String name, Long userId, String wantedImage) {
        if (!containerExists(name)) {
            log.debug("[UserContainer] useExistingContainerIfPresent: no container named {}", name);
            return null;
        }
        String currentImage = getContainerImage(name);
        if (currentImage != null && !normalizeImageRef(currentImage).equals(normalizeImageRef(wantedImage))) {
            log.info("[UserContainer] Existing container {} has image {} but config wants {}, removing and will recreate.", name, currentImage, wantedImage);
            removeContainer(name);
            return null;
        }
        if ("none".equalsIgnoreCase(getContainerNetworkMode(name))) {
            log.info("[UserContainer] Existing container {} has network=none (no DNS), removing so apt-get can work.", name);
            removeContainer(name);
            return null;
        }
        if (!isContainerRunning(name)) {
            try {
                runDocker("start", name);
                log.info("[UserContainer] Started stopped container {}", name);
            } catch (Exception e) {
                log.warn("[UserContainer] docker start failed for {}: {} (容器存在但无法 start)", name, e.getMessage());
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

    /** 精确判断同名容器是否存在（不用 docker ps -f name= 子串匹配）。 */
    private boolean containerExists(String name) {
        if (name == null || name.isBlank()) return false;
        try {
            Process p = new ProcessBuilder("docker", "inspect", name)
                    .redirectErrorStream(true).redirectError(ProcessBuilder.Redirect.PIPE).start();
            readFully(p.getInputStream());
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Scheduled(fixedDelay = 300_000) // 5 min
    public void recycleIdleContainers() {
        if (!scriptDockerProperties.enabled()) return;
        if (scriptDockerProperties.idleMinutes() <= 0) return; // ≤0 不自动回收，方便用户跑常驻任务
        long idleMs = scriptDockerProperties.idleMinutes() * 60L * 1000L;
        long now = System.currentTimeMillis();
        userContainers.entrySet().removeIf(entry -> {
            Long userId = entry.getKey();
            ContainerInfo info = entry.getValue();
            if (now - info.getLastUsedAt() < idleMs) return false;
            // 改为仅 stop 不 rm：保留容器文件系统，用户下次进入会话时可直接 start 继续使用
            try {
                if (isContainerRunning(info.containerId)) {
                    runDocker("stop", info.containerId);
                    log.info("[UserContainer] Stopped idle container for user {}", userId);
                } else {
                    log.info("[UserContainer] Container for user {} already stopped", userId);
                }
            } catch (Exception e) {
                log.warn("[UserContainer] Stop idle container failed for user {}: {}", userId, e.getMessage());
            }
            // 无论 stop 是否成功，都从内存 map 中移除；下次需要时会通过 useExistingContainerIfPresent 重新检查/启动
            return true;
        });

        // 若宿主机内存吃紧，根据配置再尝试停掉一个最久未使用的容器以释放资源
        if (scriptDockerProperties.enableHostMemoryEviction() && isHostMemoryUnderPressure()) {
            evictOldestContainerDueToLowMemory();
        }
    }

    /**
     * 精确判断指定名称/ID 的容器是否在运行。
     * 不用 docker ps -f name=xxx（会子串匹配：aimate-user-1 会匹配到 aimate-user-10，导致误判）。
     */
    private boolean isContainerRunning(String containerIdOrName) {
        if (containerIdOrName == null || containerIdOrName.isBlank()) return false;
        try {
            Process p = new ProcessBuilder("docker", "inspect", "-f", "{{.State.Running}}", containerIdOrName)
                    .redirectErrorStream(true).start();
            String out = readFully(p.getInputStream());
            p.waitFor();
            return p.exitValue() == 0 && "true".equalsIgnoreCase(out != null ? out.trim() : "");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 在执行 docker exec 前调用：若容器已停止则先 start，避免「容器已回收/停止」导致 exec 报错。
     * 解决 getOrCreateContainer 返回后、exec 执行前被空闲回收任务停掉容器的竞态。
     *
     * @return true 表示容器在运行，可安全 exec；false 表示无法启动，调用方应返回错误
     */
    public boolean ensureContainerRunning(String containerIdOrName) {
        if (containerIdOrName == null || containerIdOrName.isBlank()) return false;
        if (isContainerRunning(containerIdOrName)) return true;
        try {
            runDocker("start", containerIdOrName);
            boolean now = isContainerRunning(containerIdOrName);
            if (now) log.info("[UserContainer] Started stopped container: {}", containerIdOrName);
            return now;
        } catch (Exception e) {
            log.warn("[UserContainer] Failed to start container {}: {}", containerIdOrName, e.getMessage());
            return false;
        }
    }

    /**
     * 用户手动启动自己的容器：若已存在则 start，不存在则创建。仅操作 aimate-user-{userId}。
     * @return 容器名表示成功，null 表示 Docker 未启用、userId 为空或操作失败
     */
    @Nullable
    public String startUserContainer(Long userId) {
        return getOrCreateContainer(userId);
    }

    /**
     * 用户手动停止自己的容器：从 map 移除并执行 docker stop（仅停止，不 rm）。
     * @return true 表示已停止或本就不存在/未运行，false 表示 stop 失败
     */
    public boolean stopUserContainer(Long userId) {
        if (userId == null || !scriptDockerProperties.enabled()) return false;
        String name = CONTAINER_NAME_PREFIX + userId;
        userContainers.remove(userId);
        if (!containerExists(name)) return true;
        if (!isContainerRunning(name)) return true;
        try {
            runDocker("stop", name);
            log.info("[UserContainer] User stopped container: {}", name);
            return true;
        } catch (Exception e) {
            log.warn("[UserContainer] docker stop failed for {}: {}", name, e.getMessage());
            return false;
        }
    }

    /**
     * 用户手动重启自己的容器：先 stop 再 getOrCreate（会 start 或新建）。
     * @return 容器名表示成功，null 表示失败
     */
    @Nullable
    public String restartUserContainer(Long userId) {
        if (userId == null || !scriptDockerProperties.enabled()) return null;
        stopUserContainer(userId);
        return getOrCreateContainer(userId);
    }

    /**
     * 按配置组装 docker run：资源限制、只读根盘+tmpfs、非 root 用户、防提权等。
     * 说明：
     * - 目前仅固定启用 no-new-privileges，避免 setuid/setgid 提权；
     * - 不再强制 cap-drop=ALL，以兼容更多宿主机 / rootless Docker 环境，减少 apt 等命令的权限报错；
     * - 如需进一步能力收紧，可在确认宿主机兼容后再恢复 cap-drop 逻辑。
     */
    private List<String> buildDockerRunCommand(String containerName, String image) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("-d");
        //cmd.add("--security-opt=no-new-privileges");
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

    /**
     * 枚举所有 aimate-user-* 容器及其基本状态（不区分是否当前 JVM 已知），用于管理后台监控。
     * 为避免频繁子进程开销，建议仅在管理页面手动刷新时调用。
     *
     * 返回的 List 按 userId 升序、同一用户内部按容器名排序。
     */
    public List<ContainerSummary> listAllUserContainersWithStats() {
        List<ContainerSummary> result = new ArrayList<>();
        try {
            // 1) docker ps -a 列出所有相关容器及状态
            Process ps = new ProcessBuilder("docker", "ps", "-a",
                    "--filter", "name=" + CONTAINER_NAME_PREFIX,
                    "--format", "{{.Names}}||{{.Status}}")
                    .redirectErrorStream(true)
                    .start();
            String psOut = readFully(ps.getInputStream());
            ps.waitFor();
            if (psOut == null || psOut.isBlank()) return List.of();

            // 2) docker stats --no-stream 获取 CPU/Mem
            Process stats = new ProcessBuilder("docker", "stats", "--no-stream",
                    "--format", "{{.Name}}||{{.CPUPerc}}||{{.MemUsage}}||{{.MemPerc}}")
                    .redirectErrorStream(true)
                    .start();
            String statsOut = readFully(stats.getInputStream());
            stats.waitFor();
            Map<String, String[]> statsMap = new HashMap<>();
            if (statsOut != null && !statsOut.isBlank()) {
                for (String line : statsOut.split("\\R")) {
                    String[] parts = line.split("\\|\\|");
                    if (parts.length >= 4) {
                        statsMap.put(parts[0].trim(), new String[]{parts[1].trim(), parts[2].trim(), parts[3].trim()});
                    }
                }
            }

            // 3) 组装结果
            for (String line : psOut.split("\\R")) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\\|\\|");
                if (parts.length < 2) continue;
                String name = parts[0].trim();
                String status = parts[1].trim();
                Long userId = parseUserIdFromContainerName(name);
                if (userId == null) continue;
                ContainerInfo info = userContainers.get(userId);
                Long lastUsedAt = info != null ? info.getLastUsedAt() : null;
                // docker stats 的 .Name 可能带前导斜杠，兼容两种 key
                String[] stat = statsMap.get(name);
                if (stat == null && !name.startsWith("/")) stat = statsMap.get("/" + name);
                if (stat == null && name.startsWith("/")) stat = statsMap.get(name.substring(1));
                String cpu = stat != null ? stat[0] : null;
                String memUsage = stat != null ? stat[1] : null;
                String memPerc = stat != null ? stat[2] : null;
                result.add(new ContainerSummary(userId, name, status, lastUsedAt, cpu, memUsage, memPerc));
            }
            result.sort((a, b) -> {
                int c = Long.compare(a.userId(), b.userId());
                if (c != 0) return c;
                return a.containerName().compareToIgnoreCase(b.containerName());
            });
        } catch (Exception e) {
            log.warn("[UserContainer] listAllUserContainersWithStats failed: {}", e.getMessage());
        }
        return result;
    }

    private Long parseUserIdFromContainerName(String name) {
        if (name == null || !name.startsWith(CONTAINER_NAME_PREFIX)) return null;
        String s = name.substring(CONTAINER_NAME_PREFIX.length());
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断宿主机内存是否吃紧：优先从 /proc/meminfo 读取 MemTotal/MemAvailable，
     * 计算可用内存百分比，低于配置阈值则认为需要释放部分容器。
     */
    private boolean isHostMemoryUnderPressure() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo", StandardCharsets.UTF_8))) {
            long memTotalKb = 0L;
            long memAvailableKb = 0L;
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    memTotalKb = parseMeminfoValueKb(line);
                } else if (line.startsWith("MemAvailable:")) {
                    memAvailableKb = parseMeminfoValueKb(line);
                }
                if (memTotalKb > 0 && memAvailableKb > 0) break;
            }
            if (memTotalKb > 0 && memAvailableKb > 0) {
                int freePercent = (int) (memAvailableKb * 100 / memTotalKb);
                int threshold = Math.max(1, scriptDockerProperties.lowMemoryFreePercent());
                if (freePercent < threshold) {
                    log.info("[UserContainer] Host memory under pressure: available={}%, threshold={}%", freePercent, threshold);
                    return true;
                }
            }
        } catch (IOException e) {
            // 非 Linux 或无法读取 /proc/meminfo 时忽略该策略
        }
        return false;
    }

    private static long parseMeminfoValueKb(String line) {
        // 形如 "MemTotal:       16333780 kB"
        String[] parts = line.split("\\s+");
        for (String p : parts) {
            if (p.endsWith("kB")) continue;
            try {
                return Long.parseLong(p);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }

    /**
     * 在内存吃紧时，选择当前已知所有用户容器中「最久未使用」的一项，尝试 stop 以释放资源。
     */
    private void evictOldestContainerDueToLowMemory() {
        if (userContainers.isEmpty()) return;
        Long targetUserId = null;
        ContainerInfo targetInfo = null;
        for (Map.Entry<Long, ContainerInfo> e : userContainers.entrySet()) {
            if (targetInfo == null || e.getValue().getLastUsedAt() < targetInfo.getLastUsedAt()) {
                targetUserId = e.getKey();
                targetInfo = e.getValue();
            }
        }
        if (targetUserId == null || targetInfo == null) return;
        try {
            if (isContainerRunning(targetInfo.containerId)) {
                runDocker("stop", targetInfo.containerId);
                log.warn("[UserContainer] Stopped container for user {} due to host memory pressure", targetUserId);
            }
        } catch (Exception e) {
            log.warn("[UserContainer] Failed to stop container for user {} under memory pressure: {}", targetUserId, e.getMessage());
        } finally {
            userContainers.remove(targetUserId);
        }
    }

    public static class ContainerInfo {
        private final String containerId;
        private volatile long lastUsedAt;

        ContainerInfo(String containerId, long lastUsedAt) {
            this.containerId = containerId;
            this.lastUsedAt = lastUsedAt;
        }

        void setLastUsedAt(long lastUsedAt) { this.lastUsedAt = lastUsedAt; }
        long getLastUsedAt() { return lastUsedAt; }
    }

    /**
     * 内部使用的容器汇总信息，用于管理后台 DTO 映射。
     */
    public record ContainerSummary(
            Long userId,
            String containerName,
            String status,
            Long lastUsedAt,
            String cpuPercent,
            String memUsage,
            String memPercent
    ) {}
}
