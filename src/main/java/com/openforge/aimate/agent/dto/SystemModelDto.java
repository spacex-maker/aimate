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
                m.getPointsPer1kPromptTokens(),
                m.getPointsPer1kCompletionTokens(),
                m.getCostInputCnyPer1mPeak(),
                m.getCostInputCnyPer1mOffpeak(),
                m.getCostOutputCnyPer1mPeak(),
                m.getCostOutputCnyPer1mOffpeak()
        );
    }
}
