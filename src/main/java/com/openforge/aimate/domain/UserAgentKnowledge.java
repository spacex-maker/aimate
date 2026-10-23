package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "user_agent_knowledge", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ua_kb", columnNames = {"user_agent_id", "knowledge_base_id"})
})
public class UserAgentKnowledge extends BaseEntity {

    @Column(name = "user_agent_id", nullable = false)
    private Long userAgentId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;
}
