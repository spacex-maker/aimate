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
     * true = 仅从列表中隐藏，不物理删除会话本身及其相关数据。
     */
    @Builder.Default
    @Column(name = "hidden", nullable = false)
    private boolean hidden = false;
}
