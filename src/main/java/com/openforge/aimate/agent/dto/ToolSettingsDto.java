package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openforge.aimate.domain.UserToolSettings;

/**
 * 系统工具开关 DTO，与前端约定 camelCase。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ToolSettingsDto(
        @JsonProperty("memoryEnabled")      boolean memoryEnabled,
        @JsonProperty("webSearchEnabled")   boolean webSearchEnabled,
        @JsonProperty("createToolEnabled")  boolean createToolEnabled,
        @JsonProperty("scriptExecEnabled")  boolean scriptExecEnabled
) {
    public static ToolSettingsDto from(UserToolSettings s) {
        if (s == null) {
            return new ToolSettingsDto(true, true, true, true);
        }
        return new ToolSettingsDto(
                Boolean.TRUE.equals(s.getMemoryEnabled()),
                Boolean.TRUE.equals(s.getWebSearchEnabled()),
                Boolean.TRUE.equals(s.getCreateToolEnabled()),
                Boolean.TRUE.equals(s.getScriptExecEnabled())
        );
    }
}
