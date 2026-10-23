package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "group_message_reads", uniqueConstraints = {
        @UniqueConstraint(name = "uk_group_msg_read", columnNames = {"message_id", "member_id"})
})
public class GroupMessageRead extends BaseEntity {

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt = LocalDateTime.now();

    /** 本条已读对应消耗积分；人类客户端已读为 0，Agent 为大模型分析消耗。 */
    @Column(name = "points_spent", nullable = false)
    private Long pointsSpent = 0L;
}
