package com.openforge.aimate.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 每用户一个独立 Linux 容器的配置：隔离执行环境，可安全执行任意命令。
 * 更标准做法：资源限制（memory/cpus）、只读根盘+tmpfs、非 root 运行；可选改用 Docker Engine API（如 docker-java）替代 CLI。
 * 镜像为最小化 Linux（如 debian:bookworm-slim）；python3/node 等由 AI 通过 install_container_package 安装。
 */
@ConfigurationProperties(prefix = "agent.script.docker")
public record ScriptDockerProperties(
        @DefaultValue("true") boolean enabled,
        // 默认空闲 3 天后再回收（分钟）
        @DefaultValue("4320") int idleMinutes,
        @DefaultValue("debian:bookworm-slim") String image,
        /** 内存上限，如 4g、512m；空则不限制 */
        @DefaultValue("4g") String memoryLimit,
        /** CPU 核数上限，如 2、1.0；0 表示不限制 */
        @DefaultValue("2") double cpuLimit,
        /** 根文件系统只读，仅 /tmp 可写（需配合 tmpfs），更安全 */
        @DefaultValue("false") boolean readOnlyRootfs,
        /** 容器内运行用户，如 1000:1000；空则使用镜像默认 */
        @DefaultValue("") String runAsUser,
        /** run_container_cmd 单条命令最长执行时间（秒），长任务可调大避免超时 */
        @DefaultValue("300") int runContainerCmdTimeoutSeconds,
        /** run_container_cmd 无输出视为卡住的空闲时间（秒）；有输出则重置，仅无输出超过此时长才超时 */
        @DefaultValue("90") int runContainerCmdIdleTimeoutSeconds,
        /** 容器内脚本工具（如 Python/Shell 脚本）最长执行时间（秒） */
        @DefaultValue("300") int scriptTimeoutSeconds,
        /** 脚本工具连续无输出超过此时长视为卡住；有输出会重置计时 */
        @DefaultValue("90") int scriptIdleTimeoutSeconds,
        /** 是否在宿主机内存吃紧时主动停掉最久未使用的容器以释放资源 */
        @DefaultValue("true") boolean enableHostMemoryEviction,
        /** 当 MemAvailable / MemTotal（或等价指标）低于该百分比时视为内存吃紧，触发一次逐出 */
        @DefaultValue("10") int lowMemoryFreePercent
) {
    public boolean isCpuLimitSet() { return cpuLimit > 0; }
    public boolean isMemoryLimitSet() { return memoryLimit != null && !memoryLimit.isBlank(); }
    public boolean isRunAsUserSet() { return runAsUser != null && !runAsUser.isBlank(); }
}
