package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统级维护 Agent（非用户招聘 Agent）。当前主要用于官方知识库策展。
 */
@Getter
@Setter
@Entity
@Table(name = "system_agents")
public class SystemAgent extends BaseEntity {

    /** KB_CURATOR 等 */
    @Column(nullable = false, length = 32)
    private String type = "KB_CURATOR";

    @Column(nullable = false, length = 64, unique = true)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(nullable = false)
    private boolean enabled = true;

    /** 每日执行小时 0-23（Asia/Shanghai） */
    @Column(name = "schedule_hour", nullable = false)
    private int scheduleHour = 3;

    /** 每日执行分钟 0-59 */
    @Column(name = "schedule_minute", nullable = false)
    private int scheduleMinute = 0;

    /** 策展使用的系统模型 */
    @Column(name = "system_model_id")
    private Long systemModelId;

    /**
     * JSON 配置，见 KbCuratorConfig。
     */
    @Lob
    @Column(name = "config_json", columnDefinition = "MEDIUMTEXT")
    private String configJson;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "last_run_status", length = 32)
    private String lastRunStatus;

    @Column(name = "last_run_summary", length = 1024)
    private String lastRunSummary;

    @Column(name = "running", nullable = false)
    private boolean running = false;
}
