package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** 等级 → 系统模型池；is_default 表示该等级默认模型。 */
@Getter
@Setter
@Entity
@Table(name = "agent_tier_models", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tier_model", columnNames = {"tier", "system_model_id"})
})
public class AgentTierModel extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentTier tier;

    @Column(name = "system_model_id", nullable = false)
    private Long systemModelId;

    @Column(name = "is_default", nullable = false)
    private boolean defaultModel = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;
}
