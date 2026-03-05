package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openforge.aimate.domain.SystemConfig;

/**
 * 系统配置项 DTO，供管理端查看与编辑；字段使用 camelCase。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SystemConfigItemDto(
        @JsonProperty("id") Long id,
        @JsonProperty("configKey") String configKey,
        @JsonProperty("configValue") String configValue,
        @JsonProperty("description") String description
) {

    public static SystemConfigItemDto from(SystemConfig c) {
        if (c == null || c.getId() == null) return null;
        return new SystemConfigItemDto(
                c.getId(),
                c.getConfigKey(),
                c.getConfigValue(),
                c.getDescription()
        );
    }
}

