package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * LLM service provider registry. One provider owns one API key used by all of its system models.
 */
@Getter
@Setter
@Entity
@Table(name = "system_providers")
public class SystemProvider extends BaseEntity {

    /** Stable code matching system_models.provider, e.g. deepseek / openai / aimodel. */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    /** Provider API key. Empty means models under this provider cannot call out. */
    @Column(name = "api_key", length = 2048)
    private String apiKey;

    @Column(length = 256)
    private String description;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;
}