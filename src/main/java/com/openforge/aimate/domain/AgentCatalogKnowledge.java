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
@Table(name = "agent_catalog_knowledge", uniqueConstraints = {
        @UniqueConstraint(name = "uk_catalog_kb", columnNames = {"catalog_id", "knowledge_base_id"})
})
public class AgentCatalogKnowledge extends BaseEntity {

    @Column(name = "catalog_id", nullable = false)
    private Long catalogId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;
}
