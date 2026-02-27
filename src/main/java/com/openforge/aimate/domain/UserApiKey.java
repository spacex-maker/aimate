package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A user-owned API key for a third-party provider.
 *
 * One user can have many keys: multiple providers, multiple key types
 * (LLM / EMBEDDING / VECTOR_DB), and optionally multiple keys per slot
 * (primary + fallback).
 *
 * The agent loop resolves the active key by:
 *   SELECT * FROM user_api_keys
 *   WHERE user_id = ? AND provider = ? AND key_type = ? AND is_active = 1
 *   ORDER BY is_default DESC LIMIT 1
 */
@Getter
@Setter
@Entity
@Table(name = "user_api_keys")
public class UserApiKey extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** openai | deepseek | anthropic | azure | cohere | custom … */
    @Column(nullable = false, length = 64)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false, length = 32)
    private KeyType keyType = KeyType.LLM;

    /** User-friendly label, e.g. "公司账号" */
    @Column(length = 128)
    private String label;

    /** The raw API key value. Encrypt at rest in production. */
    @Column(name = "key_value", nullable = false, length = 512)
    private String keyValue;

    /** Custom Base URL — empty means use the provider's default. */
    @Column(name = "base_url", length = 512)
    private String baseUrl;

    /** Default model to use with this key (optional). */
    @Column(length = 128)
    private String model;

    /** True = this is the default key for (user, provider, keyType). */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    // ── Key type enum ────────────────────────────────────────────────────────

    public enum KeyType {
        LLM,
        EMBEDDING,
        VECTOR_DB,
        OTHER
    }
}
