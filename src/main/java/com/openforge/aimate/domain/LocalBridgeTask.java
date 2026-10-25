package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "local_bridge_tasks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_lbt_agent_idempotency", columnNames = {"user_agent_id", "idempotency_key"})
})
public class LocalBridgeTask extends BaseEntity {

    public enum Status {
        PENDING,
        CLAIMED,
        DONE,
        FAILED,
        CANCELLED
    }

    @Column(name = "public_id", nullable = false, length = 40, unique = true)
    private String publicId;

    @Column(name = "user_agent_id", nullable = false)
    private Long userAgentId;

    /** 群聊任务；单聊派发时为空 */
    @Column(name = "group_id")
    private Long groupId;

    /** 单聊会话 UUID；群聊派发时为空 */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** 触发消息（群消息 id 或单聊 session_message id）；可空 */
    @Column(name = "trigger_message_id")
    private Long triggerMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String instruction;

    @Lob
    @Column(name = "context_json", columnDefinition = "MEDIUMTEXT")
    private String contextJson;

    @Lob
    @Column(name = "result_text", columnDefinition = "MEDIUMTEXT")
    private String resultText;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    /** 领取租约到期；过期未 complete 则回收为 PENDING */
    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    /** 领取该任务的设备 deviceKey（可空） */
    @Column(name = "claimed_by_device_key", length = 64)
    private String claimedByDeviceKey;

    /**
     * 入队幂等键（同一 Agent 下唯一）。
     * 群聊：g:{triggerMessageId}；单聊：d:{sessionId}:{userMsgId}:{instructionHash}
     */
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * 意图世代：同 Agent + 群/会话内单调递增（通常=触发消息 id）。
     * 更大的 epoch 代表更新的用户意图；更小的任务应被取代、不得回写群聊。
     */
    @Column(name = "intent_epoch", nullable = false)
    private Long intentEpoch = 0L;

    /**
     * 已被更新意图取代。CLAIMED 被取代时置 true 并转为 CANCELLED，
     * 客户端续租看到后应中止，且 complete 不得把旧结果发到群。
     */
    @Column(name = "superseded", nullable = false)
    private Boolean superseded = Boolean.FALSE;

    /**
     * @deprecated 旧版「改气泡」流式；现改为 AGENT_THINKING + 气泡上方思考区。
     */
    @Deprecated
    @Column(name = "stream_message_id")
    private Long streamMessageId;

    /** Cursor 真思考/推理（仅 kind=thinking；complete → 群消息 thinkingText） */
    @Lob
    @Column(name = "thinking_text", columnDefinition = "MEDIUMTEXT")
    private String thinkingText;

    /** 本机执行中间输出（.partial / 工具日志；complete → 群消息 executionText） */
    @Lob
    @Column(name = "execution_text", columnDefinition = "MEDIUMTEXT")
    private String executionText;
}
