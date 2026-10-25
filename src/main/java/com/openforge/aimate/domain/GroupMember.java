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

    /**
     * 是否允许非主人的其他群成员通过 @ / 回复 触发本 Agent。
     * 仅 AGENT 有效；主人始终可触发。默认 true。
     * Agent 互 @ 另受「同主人」约束，不受本开关放开跨主人。
     */
    @Column(name = "others_may_mention", nullable = false)
    private boolean othersMayMention = true;

    /**
     * 被他人 @（含同主人其他 Agent）时是否先请示所属人类。
     * 主人本人点名不请示。默认 false。
     */
    @Column(name = "require_owner_approval_on_mention", nullable = false)
    private boolean requireOwnerApprovalOnMention = false;

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
