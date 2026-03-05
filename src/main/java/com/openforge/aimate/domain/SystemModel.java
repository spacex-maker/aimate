package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统级「模型底座」目录，供用户切换默认推理模型。
 *
 * 与 {@link UserApiKey} 配合：用户选择某条 system_model 后，用其 provider
 * 解析用户在该 provider 下的 API Key；base_url 以 system_model 或 key 为准。
 */
@Getter
@Setter
@Entity
@Table(name = "system_models")
public class SystemModel extends BaseEntity {

    /** 厂商标识，与 user_api_keys.provider 一致：openai | anthropic | google | xai | deepseek */
    @Column(nullable = false, length = 64)
    private String provider;

    /** API 模型 ID，如 gpt-5.2、claude-opus-4-5-20251101、gemini-3.1-pro-preview */
    @Column(name = "model_id", nullable = false, length = 128)
    private String modelId;

    /** 前端展示名，如 GPT-5.2 Pro、Claude 4.5 Opus */
    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    /** 该模型默认 Base URL，空则使用该 provider 的常规默认 */
    @Column(name = "base_url", length = 512)
    private String baseUrl;

    /** 排序权重，数值越小越靠前 */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    /** 是否在「切换模型」列表中展示 */
    @Column(nullable = false)
    private boolean enabled = true;

    /** 简短说明，如能力或适用场景 */
    @Column(length = 256)
    private String description;
}
