package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openforge.aimate.domain.SystemModel;

/**
 * 系统模型 DTO，供前端「切换模型」列表与选择使用；camelCase。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SystemModelDto(
        @JsonProperty("id")           long id,
        @JsonProperty("provider")     String provider,
        @JsonProperty("modelId")      String modelId,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("baseUrl")      String baseUrl,
        @JsonProperty("apiKeyConfigKey") String apiKeyConfigKey,
        @JsonProperty("sortOrder")    int sortOrder,
        @JsonProperty("enabled")      boolean enabled,
        @JsonProperty("description")  String description,
        @JsonProperty("tip")          String tip,
        @JsonProperty("supportsVision") boolean supportsVision,
        @JsonProperty("supportsThinking") boolean supportsThinking,
        @JsonProperty("contextWindowTokens") int contextWindowTokens,
        @JsonProperty("pointsPer1kPromptTokens") int pointsPer1kPromptTokens,
        @JsonProperty("pointsPer1kCompletionTokens") int pointsPer1kCompletionTokens,
        @JsonProperty("costInputCnyPer1mPeak") Double costInputCnyPer1mPeak,
        @JsonProperty("costInputCnyPer1mOffpeak") Double costInputCnyPer1mOffpeak,
        @JsonProperty("costOutputCnyPer1mPeak") Double costOutputCnyPer1mPeak,
        @JsonProperty("costOutputCnyPer1mOffpeak") Double costOutputCnyPer1mOffpeak
) {
    public static SystemModelDto from(SystemModel m) {
        if (m == null || m.getId() == null) return null;
        return new SystemModelDto(
                m.getId(),
                m.getProvider(),
                m.getModelId(),
                m.getDisplayName(),
                m.getBaseUrl(),
                m.getApiKeyConfigKey(),
                m.getSortOrder(),
                m.isEnabled(),
                m.getDescription(),
                m.getTip(),
                m.isSupportsVision(),
                m.isSupportsThinking(),
                m.getContextWindowTokens() > 0 ? m.getContextWindowTokens() : 128_000,
                m.getPointsPer1kPromptTokens(),
                m.getPointsPer1kCompletionTokens(),
                m.getCostInputCnyPer1mPeak(),
                m.getCostInputCnyPer1mOffpeak(),
                m.getCostOutputCnyPer1mPeak(),
                m.getCostOutputCnyPer1mOffpeak()
        );
    }

    /**
     * 用户侧模型列表：不返回上游地址 / Key 配置名 / 运维备注。
     * tip 仅保留面向用户的短标签（如参数量、非高峰半价）；含中转/网关/密钥字样时清空。
     */
    public static SystemModelDto forPublic(SystemModel m) {
        SystemModelDto full = from(m);
        if (full == null) return null;
        return new SystemModelDto(
                full.id(),
                full.provider(),
                full.modelId(),
                full.displayName(),
                null,
                null,
                full.sortOrder(),
                full.enabled(),
                null,
                sanitizePublicTip(full.tip()),
                full.supportsVision(),
                full.supportsThinking(),
                full.contextWindowTokens(),
                full.pointsPer1kPromptTokens(),
                full.pointsPer1kCompletionTokens(),
                full.costInputCnyPer1mPeak(),
                full.costInputCnyPer1mOffpeak(),
                full.costOutputCnyPer1mPeak(),
                full.costOutputCnyPer1mOffpeak()
        );
    }

    private static String sanitizePublicTip(String tip) {
        if (tip == null) return null;
        String t = tip.trim();
        if (t.isEmpty()) return null;
        String lower = t.toLowerCase();
        if (lower.contains("aimodel")
                || lower.contains("llama")
                || lower.contains("n_ctx")
                || lower.contains("gateway")
                || lower.contains("api key")
                || lower.contains("apikey")
                || lower.contains("amk_")
                || t.contains("\u4e2d\u8f6c")
                || t.contains("\u7f51\u5173")
                || t.contains("\u5bc6\u94a5")) {
            return null;
        }
        return t.length() > 64 ? t.substring(0, 64) : t;
    }
}