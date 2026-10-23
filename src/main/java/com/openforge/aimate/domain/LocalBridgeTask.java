package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "local_bridge_tasks")
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

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "trigger_message_id", nullable = false)
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

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
