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
@Table(name = "group_messages")
public class GroupMessage extends BaseEntity {

    public enum SenderType {
        USER,
        AGENT,
        SYSTEM
    }

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 16)
    private SenderType senderType;

    @Column(name = "sender_user_id")
    private Long senderUserId;

    @Column(name = "sender_agent_id")
    private Long senderAgentId;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "reply_to_id")
    private Long replyToId;

    @Column(name = "client_msg_id", length = 64)
    private String clientMsgId;
}
