package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 宿主机资源负载历史记录：每分钟一条，用于管理后台查看 CPU / 内存 / 磁盘一段时间内的变化。
 */
@Getter
@Setter
@Entity
@Table(
        name = "host_load_metric",
        indexes = {
                @Index(name = "idx_host_load_metric_host_time", columnList = "host_name, create_time")
        }
)
public class HostLoadMetric extends BaseEntity {

    @Column(name = "host_name", nullable = false, length = 128)
    private String hostName;

    /** 系统 CPU 负载百分比（0-100，保留一位小数） */
    @Column(name = "cpu_load_percent")
    private Double cpuLoadPercent;

    /** 可用内存百分比（0-100），与 HostResourceStatusDto.hostAvailableMemoryPercent 一致 */
    @Column(name = "mem_available_percent")
    private Integer memAvailablePercent;

    /** 根分区磁盘已用百分比（0-100） */
    @Column(name = "root_fs_used_percent")
    private Integer rootFsUsedPercent;
}

