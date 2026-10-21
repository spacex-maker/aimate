package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "agent_catalog", uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_catalog_career_tier", columnNames = {"career_id", "tier"})
})
public class AgentCatalog extends BaseEntity {

    @Column(name = "career_id", nullable = false)
    private Long careerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentTier tier;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 1024)
    private String description;

    /** 默认人设模板，供生成/雇佣时参考 */
    @Column(name = "persona_template", columnDefinition = "TEXT")
    private String personaTemplate;

    @Column(name = "default_avatar_url", length = 512)
    private String defaultAvatarUrl;

    /** MALE / FEMALE / OTHER */
    @Column(name = "default_gender", length = 32)
    private String defaultGender;

    @Column(name = "price_day_points", nullable = false)
    private long priceDayPoints = 0;

    @Column(name = "price_month_points", nullable = false)
    private long priceMonthPoints = 0;

    @Column(name = "price_year_points", nullable = false)
    private long priceYearPoints = 0;

    /** 同期 Soul 席位积分价（日/月/年） */
    @Column(name = "soul_price_day_points", nullable = false)
    private long soulPriceDayPoints = 0;

    @Column(name = "soul_price_month_points", nullable = false)
    private long soulPriceMonthPoints = 0;

    @Column(name = "soul_price_year_points", nullable = false)
    private long soulPriceYearPoints = 0;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;
}
