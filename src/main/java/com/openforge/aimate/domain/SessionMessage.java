package com.openforge.aimate.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 会话内单条消息，对应 LLM 对话中的一条 role/content/tool_calls 记录。
 * 与 agent_sessions.context_window（整段 JSON）并存：本表便于按条查询、分页与检索。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "agent_session_messages",
    uniqueConstraints = @UniqueConstraint(name = "uq_session_seq", columnNames = { "agent_session_id", "seq" })
)
public class SessionMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_session_id", nullable = false, updatable = false)
    private AgentSession agentSession;

    /** 会话内顺序，从 0 开始。 */
    @Column(name = "seq", nullable = false, updatable = false)
    private Integer seq;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    /** 正文；assistant 仅含 tool_calls 时可为空。 */
    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    /** 用户消息附带的图片 URL JSON 数组（Chat COS HTTPS），用于回显并在视觉模型请求中发送。 */
    @Column(name = "attachments_json", columnDefinition = "LONGTEXT")
    private String attachmentsJson;

    /** role=tool 时对应 ToolCall.id。 */
    @Column(name = "tool_call_id", length = 64)
    private String toolCallId;

    /** role=assistant 时的 tool_calls 数组 JSON。 */
    @Column(name = "tool_calls_json", columnDefinition = "TEXT")
    private String toolCallsJson;

    /** 仅 assistant 使用：回答中 / 已完成 / 已中断，可被用户中断。 */
    @Column(name = "message_status", length = 20)
    private String messageStatus;

    /** 仅 assistant：思考过程正文，前端可折叠展示。 */
    @Column(name = "thinking_content", columnDefinition = "LONGTEXT")
    private String thinkingContent;

    /**
     * 仅 assistant：本条回复的思考+工具调用时间线（StreamBlock JSON 数组），
     * 用于历史回放时还原与实时思考相同的展示效果。
     */
    @Column(name = "thinking_blocks_json", columnDefinition = "LONGTEXT")
    private String thinkingBlocksJson;

    /** 归属某条 assistant 回复（如工具消息）；NULL=主序消息。 */
    @Column(name = "reply_to_message_id")
    private Long replyToMessageId;

    /**
     * true = 已纳入上下文压缩摘要，送模时不再附带原文；UI 历史仍展示。
     */
    @Builder.Default
    @Column(name = "exclude_from_context", nullable = false)
    private boolean excludeFromContext = false;

    /**
     * true = 钉住：压缩时永不 exclude；约束/决策/关键 ID 等应 pin。
     */
    @Builder.Default
    @Column(name = "pinned", nullable = false)
    private boolean pinned = false;

    public static final String STATUS_ANSWERING = "ANSWERING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_INTERRUPTED = "INTERRUPTED";
}
