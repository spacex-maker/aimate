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

    /**
     * system_config.config_key，用于查找该模型使用的系统级 API Key；
     * 为空则使用该 provider 的默认（如 application.yml 或环境变量中的配置）。
     */
    @Column(name = "api_key_config_key", length = 128)
    private String apiKeyConfigKey;

    /** 排序权重，数值越小越靠前 */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    /** 是否在「切换模型」列表中展示 */
    @Column(nullable = false)
    private boolean enabled = true;

    /** 是否接受图片输入。当前仅 DeepSeek V4.1 Flash。 */
    @Column(name = "supports_vision", nullable = false)
    private boolean supportsVision = false;

    /** 模型上下文窗口上限（token），用于前端占用率圆环。 */
    @Column(name = "context_window_tokens", nullable = false)
    private int contextWindowTokens = 128_000;

    /** 简短说明，如能力或适用场景 */
    @Column(length = 256)
    private String description;

    /**
     * 选模型时展示在名称旁的提示，如「非高峰半价」。
     * 与 description 区分：tip 面向选模型场景的短标签。
     */
    @Column(length = 128)
    private String tip;

    @Column(name = "points_per_1k_prompt_tokens", nullable = false)
    private int pointsPer1kPromptTokens = 1;

    @Column(name = "points_per_1k_completion_tokens", nullable = false)
    private int pointsPer1kCompletionTokens = 3;

    @Column(name = "cost_input_cny_per_1m_peak")
    private Double costInputCnyPer1mPeak;

    @Column(name = "cost_input_cny_per_1m_offpeak")
    private Double costInputCnyPer1mOffpeak;

    @Column(name = "cost_output_cny_per_1m_peak")
    private Double costOutputCnyPer1mPeak;

    @Column(name = "cost_output_cny_per_1m_offpeak")
    private Double costOutputCnyPer1mOffpeak;
}
