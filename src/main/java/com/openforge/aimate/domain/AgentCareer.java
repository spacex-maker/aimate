package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "agent_careers")
public class AgentCareer extends BaseEntity {

    /** 所属行业；可空以兼容迁移中的旧数据，招聘页归入「其他」。 */
    @Column(name = "industry_id")
    private Long industryId;

    @Column(nullable = false, length = 64, unique = true)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    /** 默认执行方式；LOCAL_BRIDGE 招聘后由本机 AI 拉任务。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 32)
    private AgentExecutionMode executionMode = AgentExecutionMode.CLOUD_LLM;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(nullable = false)
    private boolean enabled = true;
}
