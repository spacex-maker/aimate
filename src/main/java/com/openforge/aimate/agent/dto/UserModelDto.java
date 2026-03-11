package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openforge.aimate.domain.UserApiKey;

/**
 * 用户自有模型（来自 user_api_keys，key_type=LLM），用于前端在「我的模型」区域展示。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record UserModelDto(
        @JsonProperty("id")        Long id,
        @JsonProperty("provider")  String provider,
        @JsonProperty("label")     String label,
        @JsonProperty("model")     String model,
        @JsonProperty("baseUrl")   String baseUrl
) {
    public static UserModelDto from(UserApiKey key) {
        if (key == null || key.getId() == null) return null;
        return new UserModelDto(
                key.getId(),
                key.getProvider(),
                key.getLabel(),
                key.getModel(),
                key.getBaseUrl()
        );
    }
}

