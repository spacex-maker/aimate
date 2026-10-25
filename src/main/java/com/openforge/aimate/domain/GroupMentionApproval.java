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
@Table(name = "group_mention_approvals", uniqueConstraints = {
        @UniqueConstraint(name = "uk_gma_trigger_target", columnNames = {"trigger_message_id", "target_member_id"})
})
public class GroupMentionApproval extends BaseEntity {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "card_message_id", nullable = false)
    private Long cardMessageId;

    @Column(name = "trigger_message_id", nullable = false)
    private Long triggerMessageId;

    @Column(name = "target_member_id", nullable = false)
    private Long targetMemberId;

    @Column(name = "target_user_agent_id", nullable = false)
    private Long targetUserAgentId;

    @Column(name = "requester_user_id")
    private Long requesterUserId;

    @Column(name = "requester_agent_id")
    private Long requesterAgentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "decided_by_user_id")
    private Long decidedByUserId;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;
}
