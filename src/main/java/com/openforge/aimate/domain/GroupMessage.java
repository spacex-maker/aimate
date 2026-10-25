package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
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

    /** 普通聊天 vs 被 @ 请示主人审批卡 */
    public enum MessageKind {
        CHAT,
        MENTION_APPROVAL
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

    /** 气泡上方：模型真思考/推理（可空；勿塞执行日志） */
    @Lob
    @Column(name = "thinking_text", columnDefinition = "MEDIUMTEXT")
    private String thinkingText;

    /** 气泡上方：本机执行中间输出（.partial 等；可空） */
    @Lob
    @Column(name = "execution_text", columnDefinition = "MEDIUMTEXT")
    private String executionText;

    @Column(name = "reply_to_id")
    private Long replyToId;

    @Column(name = "client_msg_id", length = 64)
    private String clientMsgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_kind", nullable = false, length = 32)
    private MessageKind messageKind = MessageKind.CHAT;
}
