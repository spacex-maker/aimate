package com.openforge.aimate.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Assistant 回复的一个历史版本；重试会追加新版本，上下文给 AI 时用 session_message.content（最新）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agent_assistant_message_versions")
public class AssistantMessageVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_session_message_id", nullable = false, updatable = false)
    private SessionMessage agentSessionMessage;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "create_time", nullable = false, updatable = false)
    private java.time.LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) createTime = java.time.LocalDateTime.now();
    }
}
