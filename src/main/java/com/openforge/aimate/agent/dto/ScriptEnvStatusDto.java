package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 当前用户的脚本执行环境状态（是否启用 Docker、容器是否在运行、镜像与资源限制等），用于在会话页顶部展示。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScriptEnvStatusDto(
        boolean dockerEnabled,
        String containerStatus,
        String containerName,
        Integer idleMinutes,
        String message,
        Boolean dockerAvailable,
        String dockerVersion,
        /** 当前环境使用的虚拟机基础镜像（如 debian:bookworm-slim） */
        String image,
        /** 容器内存上限，如 256m */
        String memoryLimit,
        /** 容器 CPU 核数上限，如 0.5 */
        Double cpuLimit
) {
    /** 仅本机执行，未启用 Docker；dockerAvailable/dockerVersion 表示本机是否已安装 Docker */
    public static ScriptEnvStatusDto hostOnly(String message, Boolean dockerAvailable, String dockerVersion) {
        return new ScriptEnvStatusDto(false, "none", null, null, message, dockerAvailable, dockerVersion, null, null, null);
    }

    /** Docker 已启用，当前用户有运行中容器 */
    public static ScriptEnvStatusDto dockerRunning(String containerName, int idleMinutes, Boolean dockerAvailable, String dockerVersion,
                                                   String image, String memoryLimit, Double cpuLimit) {
        return new ScriptEnvStatusDto(true, "running", containerName, idleMinutes,
                "脚本在 Docker 容器中执行，空闲 %d 分钟后将自动回收".formatted(idleMinutes), dockerAvailable, dockerVersion, image, memoryLimit, cpuLimit);
    }

    /** Docker 已启用，当前用户暂无容器（首次执行脚本时会自动创建） */
    public static ScriptEnvStatusDto dockerNoContainer(int idleMinutes, Boolean dockerAvailable, String dockerVersion,
                                                       String image, String memoryLimit, Double cpuLimit) {
        return new ScriptEnvStatusDto(true, "none", null, idleMinutes,
                "脚本将在 Docker 容器中执行，首次调用工具时自动创建容器，空闲 %d 分钟后回收".formatted(idleMinutes), dockerAvailable, dockerVersion, image, memoryLimit, cpuLimit);
    }
}
