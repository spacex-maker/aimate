package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 单个用户容器的运行状态与资源占用，用于管理后台监控页面。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserContainerStatusDto(
        Long   userId,
        String username,
        String containerName,
        String status,       // running | exited | created | unknown
        Long   lastUsedAt,   // epoch ms, 可能为 null（重启后 map 中无记录）
        String cpuPercent,   // 如 "0.15%"
        String memUsage,     // 如 "128MiB / 4GiB"
        String memPercent    // 如 "3.2%"
) {}
