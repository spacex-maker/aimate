package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "agent_daily_points", uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_daily_points", columnNames = {"user_agent_id", "day"})
})
public class AgentDailyPoints extends BaseEntity {

    @Column(name = "user_agent_id", nullable = false)
    private Long userAgentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate day;

    @Column(name = "spent_points", nullable = false)
    private long spentPoints = 0;
}
