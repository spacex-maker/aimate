package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openforge.aimate.domain.UserDefaultModel;

/**
 * 当前用户首选模型（最近一次在输入框选择的模型）DTO。
 * 仅用于前端恢复默认选中项。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record UserDefaultModelDto(
        @JsonProperty("source")       String source,
        @JsonProperty("systemModelId") Long systemModelId,
        @JsonProperty("userApiKeyId") Long userApiKeyId
) {
    public static UserDefaultModelDto from(UserDefaultModel udm) {
        if (udm == null) {
            return new UserDefaultModelDto(null, null, null);
        }
        String source = udm.getSource();
        Long systemId = udm.getSystemModel() != null ? udm.getSystemModel().getId() : null;
        Long keyId    = udm.getUserApiKey() != null ? udm.getUserApiKey().getId() : null;
        return new UserDefaultModelDto(source, systemId, keyId);
    }
}

