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

    public enum SessionStatus {
        PENDING,
        RUNNING,
        PAUSED,
        COMPLETED,
        FAILED
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
    private SessionStatus status = SessionStatus.PENDING;

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

    /** Populated when status = COMPLETED. */
    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    /** Populated when status = FAILED. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
