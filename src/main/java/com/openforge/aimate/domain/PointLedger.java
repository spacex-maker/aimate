package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "point_ledger")
public class PointLedger extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long delta;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(nullable = false, length = 32)
    private String reason;

    @Column(name = "ref_type", length = 32)
    private String refType;

    @Column(name = "ref_id", length = 64)
    private String refId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "llm_call_log_id")
    private Long llmCallLogId;

    @Column(length = 256)
    private String remark;

    public static final String CHAT_DEDUCT = "CHAT_DEDUCT";
    public static final String RECHARGE = "RECHARGE";
    public static final String ADMIN_ADJUST = "ADMIN_ADJUST";
    public static final String SYSTEM_GIFT = "SYSTEM_GIFT";
    public static final String AGENT_RENT = "AGENT_RENT";
    public static final String SOUL_SEAT = "SOUL_SEAT";
}
