package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.HostResourceStatusDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 管理员查看宿主机与 Docker 资源配置摘要，用于容器监控页顶部展示。
 * 路径受 SecurityConfig 中 /api/admin/** + ADMIN 权限保护。
 */
@RestController
@RequestMapping("/api/admin/host-status")
@RequiredArgsConstructor
public class AdminHostStatusController {

    private final ScriptDockerProperties scriptDockerProperties;

    @GetMapping
    public ResponseEntity<HostResourceStatusDto> getHostStatus() {
        // 宿主机 CPU 信息
        int cpuCores = Runtime.getRuntime().availableProcessors();

        // 宿主机内存与 CPU 负载：优先使用 OperatingSystemMXBean（适配 Windows/Linux/Mac），若不可用再回退到 /proc/meminfo（Linux）
        long totalMemBytes = 0L;
        long availableMemBytes = 0L;
        Double systemCpuLoadPercent = null;
        try {
            java.lang.management.OperatingSystemMXBean osBean =
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                totalMemBytes = sunOs.getTotalPhysicalMemorySize();
                long free = sunOs.getFreePhysicalMemorySize();
                availableMemBytes = free;
                double load = sunOs.getSystemCpuLoad(); // 0.0 ~ 1.0，负数表示不可用
                if (load >= 0.0) {
                    systemCpuLoadPercent = Math.round(load * 1000.0) / 10.0; // 保留 1 位小数
                }
            }
        } catch (Exception ignored) {
        }
        // 回退方案：Linux 上从 /proc/meminfo 读取
        if (totalMemBytes <= 0 || availableMemBytes <= 0) {
            try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo", StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("MemTotal:")) {
                        totalMemBytes = parseMeminfoValueKb(line) * 1024;
                    } else if (line.startsWith("MemAvailable:")) {
                        availableMemBytes = parseMeminfoValueKb(line) * 1024;
                    }
                    if (totalMemBytes > 0 && availableMemBytes > 0) break;
                }
            } catch (IOException ignored) {
                // 非 Linux 或无法读取 /proc/meminfo 时保持为 0
            }
        }
        Integer availablePercent = null;
        if (totalMemBytes > 0 && availableMemBytes > 0) {
            availablePercent = (int) (availableMemBytes * 100 / totalMemBytes);
        }

        // 根分区磁盘使用率
        Integer rootFsUsedPercent = null;
        try {
            File root = new File(File.separator);
            long totalSpace = root.getTotalSpace();
            long usableSpace = root.getUsableSpace();
            if (totalSpace > 0) {
                long used = totalSpace - usableSpace;
                rootFsUsedPercent = (int) (used * 100 / totalSpace);
            }
        } catch (Exception ignored) {
        }

        String image = scriptDockerProperties.image();
        String memoryLimit = scriptDockerProperties.memoryLimit();
        Double cpuLimit = scriptDockerProperties.cpuLimit();
        Integer lowMemThreshold = scriptDockerProperties.lowMemoryFreePercent();

        String msg = "宿主机 %d 核，内存合计 %.1f GB，可用 %.1f GB（约 %s%%）；根分区已用约 %s%%。容器镜像 %s，内存上限 %s，CPU 上限 %s 核，当可用内存低于 %d%% 时将按需回收空闲容器。"
                .formatted(
                        cpuCores,
                        totalMemBytes > 0 ? totalMemBytes / 1024.0 / 1024 / 1024 : 0.0,
                        availableMemBytes > 0 ? availableMemBytes / 1024.0 / 1024 / 1024 : 0.0,
                        availablePercent != null ? availablePercent : "未知",
                        rootFsUsedPercent != null ? rootFsUsedPercent : "未知",
                        image,
                        memoryLimit != null ? memoryLimit : "未限制",
                        cpuLimit != null && cpuLimit > 0 ? cpuLimit : 0,
                        lowMemThreshold
                );

        HostResourceStatusDto dto = new HostResourceStatusDto(
                cpuCores > 0 ? cpuCores : null,
                systemCpuLoadPercent,
                totalMemBytes > 0 ? totalMemBytes : null,
                availableMemBytes > 0 ? availableMemBytes : null,
                availablePercent,
                rootFsUsedPercent,
                image,
                memoryLimit,
                cpuLimit,
                lowMemThreshold,
                msg
        );
        return ResponseEntity.ok(dto);
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
}

