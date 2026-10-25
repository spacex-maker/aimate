package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 群内单个 Agent 的上下文压缩状态。
 * 聊天记录仍在 group_messages；本表只存结构化摘要 + 已折叠水位。
 */
@Getter
@Setter
@Entity
@Table(
        name = "group_agent_contexts",
        uniqueConstraints = @UniqueConstraint(name = "uk_gac_group_agent", columnNames = {"group_id", "user_agent_id"})
)
public class GroupAgentContext extends BaseEntity {

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_agent_id", nullable = false)
    private Long userAgentId;

    /** 结构化 SessionState JSON（与 agent_sessions.context_summary 同格式） */
    @Column(name = "context_summary", columnDefinition = "LONGTEXT")
    private String contextSummary;

    /**
     * 已折叠进摘要的最大 group_messages.id（含）；
     * 送模时只取 id &gt; 此值的近期原文 + 摘要。
     */
    @Column(name = "folded_through_message_id")
    private Long foldedThroughMessageId;
}
