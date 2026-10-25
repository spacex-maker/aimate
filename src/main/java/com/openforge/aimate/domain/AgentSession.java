package com.openforge.aimate.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents one end-to-end Agent thinking session.
 *
 * The entire cognitive state of the Agent lives here — the Java process
 * itself is intentionally stateless.  A crash or blue-green restart at
 * any point can be recovered by reloading this row.
 *
 * Key design notes:
 *
 *  contextWindow  — serialized List<Message> (JSON array).  This is what
 *                   gets sent to the LLM on every iteration.  Stored as
 *                   LONGTEXT because context windows can be very large.
 *
 *  currentPlan    — serialized List<PlanStep> (JSON array) produced by the
 *                   Plan phase of Plan-and-Execute.  Each step carries its
 *                   own status so the Agent can resume mid-plan.
 *
 *  iterationCount — monotonically incremented each Agent loop; serves as a
 *                   safety breaker if the Agent gets stuck in a cycle.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "agent_sessions",
    uniqueConstraints = @UniqueConstraint(name = "uq_session_id", columnNames = "session_id")
)
public class AgentSession extends BaseEntity {

    /**
     * 会话状态 = 是否有存活线程，与单轮成败无关。
     * IDLE=静默（无线程，可收新任务），ACTIVE=在线执行，PAUSED=暂停。
     * 单轮结果在 result / error_message。
     */
    public enum SessionStatus {
        /** 静默：无 Agent 线程，可发送新消息开始新一轮 */
        IDLE,
        /** 在线：有线程在跑（思考/执行工具） */
        ACTIVE,
        /** 已暂停：线程存活但挂起，可恢复 */
        PAUSED,
        /** @deprecated 兼容旧数据，视为 IDLE */
        @Deprecated PENDING,
        /** @deprecated 兼容旧数据，视为 ACTIVE */
        @Deprecated RUNNING,
        /** @deprecated 兼容旧数据，视为 IDLE */
        @Deprecated COMPLETED,
        /** @deprecated 兼容旧数据，视为 IDLE */
        @Deprecated FAILED
    }

    /** Owner of this session — FK to users.id (nullable for backward compat). */
    @Column(name = "user_id")
    private Long userId;

    /** 绑定的 UserAgent；P1 单聊必填（旧数据可为空，发送时回退秘书）。 */
    @Column(name = "user_agent_id")
    private Long userAgentId;

    /** External UUID passed in by the caller. */
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    /** The original task the user asked the Agent to solve. */
    @Column(name = "task_description", nullable = false, columnDefinition = "TEXT")
    private String taskDescription;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SessionStatus status = SessionStatus.IDLE;

    /**
     * JSON-serialized List<PlanStep>.
     * Null until the Planner has run for the first time.
     */
    @Column(name = "current_plan", columnDefinition = "TEXT")
    private String currentPlan;

    /**
     * JSON-serialized List<Message> — the full conversation history
     * that will be sent to the LLM on the next iteration.
     */
    @Column(name = "context_window", columnDefinition = "LONGTEXT")
    private String contextWindow;

    /**
     * 上下文压缩摘要（滚动 summary）：替代已被 exclude_from_context 的更早轮次，
     * 送模时插在系统提示之后、近期原文之前（Cursor / OpenAI Session Memory 同款）。
     */
    @Column(name = "context_summary", columnDefinition = "LONGTEXT")
    private String contextSummary;

    @Builder.Default
    @Column(name = "iteration_count", nullable = false)
    private Integer iterationCount = 0;

    /** 上一轮回答正文（单轮成功时有值） */
    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    /** 上一轮错误信息（单轮失败或中断时有值） */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 当前正在回答的 assistant 占位消息 id，可被用户中断后更新为 INTERRUPTED。 */
    @Column(name = "current_assistant_message_id")
    private Long currentAssistantMessageId;

    /**
     * 是否在「最近会话」等列表中隐藏该会话。
     * true = 仅从列表中隐藏，不物理删除会话本身及其相关数据（归档）。
     */
    @Builder.Default
    @Column(name = "hidden", nullable = false)
    private boolean hidden = false;

    /** 置顶时间；非空表示置顶，越新越靠前 */
    @Column(name = "pinned_at")
    private java.time.LocalDateTime pinnedAt;

    /** 用户最近一次发消息时间；会话列表按此倒序（发消息后置顶） */
    @Column(name = "last_user_message_at")
    private java.time.LocalDateTime lastUserMessageAt;

    /**
     * 单聊是否已在聊天框改过系统工具开关。
     * false/null → 跟随 UserAgent 默认；true → 使用下方四列。
     * Agent 默认变更时会清回 false，使所有单聊重新跟随。
     */
    @Builder.Default
    @Column(name = "tool_settings_overridden", nullable = false)
    private boolean toolSettingsOverridden = false;

    @Column(name = "tool_memory_enabled")
    private Boolean toolMemoryEnabled;

    @Column(name = "tool_web_search_enabled")
    private Boolean toolWebSearchEnabled;

    @Column(name = "tool_create_tool_enabled")
    private Boolean toolCreateToolEnabled;

    @Column(name = "tool_script_exec_enabled")
    private Boolean toolScriptExecEnabled;
}
