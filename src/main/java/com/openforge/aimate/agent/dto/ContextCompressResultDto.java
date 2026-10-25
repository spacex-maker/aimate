package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 手动上下文压缩结果（对齐 Cursor summarize / OpenAI Session Memory）。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ContextCompressResultDto(
        @JsonProperty("ok") boolean ok,
        @JsonProperty("compressedMessages") int compressedMessages,
        @JsonProperty("keptRecentMessages") int keptRecentMessages,
        @JsonProperty("summaryChars") int summaryChars,
        @JsonProperty("usageBefore") ContextUsageDto usageBefore,
        @JsonProperty("usageAfter") ContextUsageDto usageAfter,
        @JsonProperty("message") String message
) {}
