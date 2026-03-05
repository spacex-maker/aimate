package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 宿主机资源状态摘要：CPU / 内存 / 磁盘 以及容器资源限制等，用于管理后台容器监控页顶部展示。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HostResourceStatusDto(
        Integer cpuCores,
        Double systemCpuLoadPercent,
        Long hostTotalMemoryBytes,
        Long hostAvailableMemoryBytes,
        Integer hostAvailableMemoryPercent,
        Integer rootFsUsedPercent,
        Long rootFsTotalBytes,
        String dockerImage,
        String dockerMemoryLimit,
        Double dockerCpuLimit,
        Integer lowMemoryFreePercent,
        String message
) {
}

