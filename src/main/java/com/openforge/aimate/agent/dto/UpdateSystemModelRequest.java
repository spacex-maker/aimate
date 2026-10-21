package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 管理员更新系统模型属性请求体。
 */
public record UpdateSystemModelRequest(
        @JsonProperty("enabled")   Boolean enabled,
        @JsonProperty("sortOrder") Integer sortOrder,
        /** 关联 system_config.config_key；空字符串表示清空。 */
        @JsonProperty("apiKeyConfigKey") String apiKeyConfigKey,
        /** 选模型时名称旁的提示；空字符串表示清空。 */
        @JsonProperty("tip") String tip,
        @JsonProperty("pointsPer1kPromptTokens") Integer pointsPer1kPromptTokens,
        @JsonProperty("pointsPer1kCompletionTokens") Integer pointsPer1kCompletionTokens,
        @JsonProperty("costInputCnyPer1mPeak") Double costInputCnyPer1mPeak,
        @JsonProperty("costInputCnyPer1mOffpeak") Double costInputCnyPer1mOffpeak,
        @JsonProperty("costOutputCnyPer1mPeak") Double costOutputCnyPer1mPeak,
        @JsonProperty("costOutputCnyPer1mOffpeak") Double costOutputCnyPer1mOffpeak
) {}
