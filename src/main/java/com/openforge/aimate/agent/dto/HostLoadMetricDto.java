package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openforge.aimate.domain.HostLoadMetric;

import java.time.LocalDateTime;

/**
 * 宿主机负载历史记录 DTO，供管理端图表展示。
 */
public record HostLoadMetricDto(
        @JsonProperty("hostName") String hostName,
        @JsonProperty("timestamp") LocalDateTime timestamp,
        @JsonProperty("cpuLoadPercent") Double cpuLoadPercent,
        @JsonProperty("memAvailablePercent") Integer memAvailablePercent,
        @JsonProperty("rootFsUsedPercent") Integer rootFsUsedPercent
) {

    public static HostLoadMetricDto from(HostLoadMetric e) {
        if (e == null) return null;
        return new HostLoadMetricDto(
                e.getHostName(),
                e.getCreateTime(),
                e.getCpuLoadPercent(),
                e.getMemAvailablePercent(),
                e.getRootFsUsedPercent()
        );
    }
}

