package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "group_members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_group_user", columnNames = {"group_id", "user_id"}),
        @UniqueConstraint(name = "uk_group_agent", columnNames = {"group_id", "user_agent_id"})
})
public class GroupMember extends BaseEntity {

    public enum MemberType {
        USER,
        AGENT
    }

    public enum Role {
        OWNER,
        ADMIN,
        MEMBER
    }

    public enum AgentReplyMode {
        SILENT,
        MENTION_ONLY,
        ALWAYS,
        SMART
    }

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_type", nullable = false, length = 16)
    private MemberType memberType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_agent_id")
    private Long userAgentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_reply_mode", length = 32)
    private AgentReplyMode agentReplyMode;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    @Column(name = "muted_until")
    private LocalDateTime mutedUntil;

    /** 我在本群的昵称（仅 USER） */
    @Column(length = 64)
    private String nickname;

    /** 我对这个群的备注（仅 USER，会话列表优先显示） */
    @Column(length = 128)
    private String remark;

    /** 置顶时间 */
    @Column(name = "pinned_at")
    private LocalDateTime pinnedAt;

    /** 归档后不出现在侧栏会话区 */
    @Column(nullable = false)
    private boolean archived = false;
}
