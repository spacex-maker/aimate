package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openforge.aimate.agent.compress.IntelligentCompressArch;

/**
 * Manual context compress result (ICA pipeline).
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ContextCompressResultDto(
        @JsonProperty("ok") boolean ok,
        @JsonProperty("compressedMessages") int compressedMessages,
        @JsonProperty("keptRecentMessages") int keptRecentMessages,
        @JsonProperty("summaryChars") int summaryChars,
        @JsonProperty("usageBefore") ContextUsageDto usageBefore,
        @JsonProperty("usageAfter") ContextUsageDto usageAfter,
        @JsonProperty("message") String message,
        @JsonProperty("compressArchitecture") String compressArchitecture,
        @JsonProperty("compressArchitectureName") String compressArchitectureName,
        @JsonProperty("compressVersion") String compressVersion,
        @JsonProperty("compressPolicyId") String compressPolicyId
) {
    public static ContextCompressResultDto of(
            boolean ok,
            int compressedMessages,
            int keptRecentMessages,
            int summaryChars,
            ContextUsageDto usageBefore,
            ContextUsageDto usageAfter,
            String message
    ) {
        return new ContextCompressResultDto(
                ok, compressedMessages, keptRecentMessages, summaryChars,
                usageBefore, usageAfter, message,
                IntelligentCompressArch.ID,
                IntelligentCompressArch.DISPLAY_NAME_ZH,
                IntelligentCompressArch.VERSION,
                IntelligentCompressArch.POLICY_ID);
    }
}