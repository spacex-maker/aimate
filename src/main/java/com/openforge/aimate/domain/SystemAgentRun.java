package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "system_agent_runs")
public class SystemAgentRun extends BaseEntity {

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "trigger_type", nullable = false, length = 16)
    private String triggerType = "SCHEDULE"; // SCHEDULE | MANUAL

    @Column(nullable = false, length = 32)
    private String status = "RUNNING"; // RUNNING | SUCCESS | FAILED | PARTIAL

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(length = 1024)
    private String summary;

    @Lob
    @Column(name = "detail_json", columnDefinition = "MEDIUMTEXT")
    private String detailJson;
}
