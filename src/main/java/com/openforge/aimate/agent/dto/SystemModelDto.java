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
        @JsonProperty("modelId")     String modelId,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("baseUrl")     String baseUrl,
        @JsonProperty("enabled")     boolean enabled,
        @JsonProperty("description") String description
) {
    public static SystemModelDto from(SystemModel m) {
        if (m == null || m.getId() == null) return null;
        return new SystemModelDto(
                m.getId(),
                m.getProvider(),
                m.getModelId(),
                m.getDisplayName(),
                m.getBaseUrl(),
                m.isEnabled(),
                m.getDescription()
        );
    }
}
