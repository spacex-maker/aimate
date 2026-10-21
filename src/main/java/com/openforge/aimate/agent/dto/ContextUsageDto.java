package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 会话上下文占用估算，供输入框圆环展示（类似 Cursor context ring）。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ContextUsageDto(
        @JsonProperty("usedTokens") int usedTokens,
        @JsonProperty("limitTokens") int limitTokens,
        @JsonProperty("percent") double percent,
        @JsonProperty("messageTokens") int messageTokens,
        @JsonProperty("toolsTokens") int toolsTokens
) {
    public static ContextUsageDto of(int messageTokens, int toolsTokens, int limitTokens) {
        int used = Math.max(0, messageTokens) + Math.max(0, toolsTokens);
        int limit = Math.max(1, limitTokens);
        double percent = Math.min(100.0, Math.round(used * 1000.0 / limit) / 10.0);
        return new ContextUsageDto(used, limit, percent, Math.max(0, messageTokens), Math.max(0, toolsTokens));
    }
}
