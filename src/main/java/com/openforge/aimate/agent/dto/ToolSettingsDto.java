package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openforge.aimate.domain.UserToolSettings;

/**
 * 系统工具开关 DTO，与前端约定 camelCase。
 * overridden：单聊场景下是否已在聊天框改过（未改则跟随 Agent 默认）。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolSettingsDto(
        @JsonProperty("memoryEnabled")      boolean memoryEnabled,
        @JsonProperty("webSearchEnabled")   boolean webSearchEnabled,
        @JsonProperty("createToolEnabled")  boolean createToolEnabled,
        @JsonProperty("scriptExecEnabled")  boolean scriptExecEnabled,
        @JsonProperty("overridden")         Boolean overridden
) {
    public ToolSettingsDto(
            boolean memoryEnabled,
            boolean webSearchEnabled,
            boolean createToolEnabled,
            boolean scriptExecEnabled
    ) {
        this(memoryEnabled, webSearchEnabled, createToolEnabled, scriptExecEnabled, null);
    }

    public static ToolSettingsDto from(UserToolSettings s) {
        if (s == null) {
            return defaults();
        }
        return new ToolSettingsDto(
                Boolean.TRUE.equals(s.getMemoryEnabled()),
                Boolean.TRUE.equals(s.getWebSearchEnabled()),
                Boolean.TRUE.equals(s.getCreateToolEnabled()),
                Boolean.TRUE.equals(s.getScriptExecEnabled())
        );
    }

    public static ToolSettingsDto of(
            boolean memoryEnabled,
            boolean webSearchEnabled,
            boolean createToolEnabled,
            boolean scriptExecEnabled,
            Boolean overridden
    ) {
        return new ToolSettingsDto(
                memoryEnabled, webSearchEnabled, createToolEnabled, scriptExecEnabled, overridden);
    }

    public static ToolSettingsDto defaults() {
        return new ToolSettingsDto(true, true, true, true);
    }
}
