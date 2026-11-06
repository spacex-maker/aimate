package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openforge.aimate.agent.compress.IntelligentCompressArch;

/**
 * Session context usage estimate (Cursor-style ring) + ICA compress policy snapshot.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ContextUsageDto(
        @JsonProperty("usedTokens") int usedTokens,
        @JsonProperty("limitTokens") int limitTokens,
        @JsonProperty("percent") double percent,
        @JsonProperty("messageTokens") int messageTokens,
        @JsonProperty("toolsTokens") int toolsTokens,
        @JsonProperty("systemTokens") int systemTokens,
        @JsonProperty("conversationTokens") int conversationTokens,
        @JsonProperty("compressRecommended") boolean compressRecommended,
        @JsonProperty("compressAvailable") boolean compressAvailable,
        /** none | suggest | urgent */
        @JsonProperty("compressUrgency") String compressUrgency,
        @JsonProperty("compressHint") String compressHint,
        /** e.g. aimate-ica */
        @JsonProperty("compressArchitecture") String compressArchitecture,
        /** Product name, e.g. 智能压缩 */
        @JsonProperty("compressArchitectureName") String compressArchitectureName,
        /** Semver, e.g. 1.0.0 */
        @JsonProperty("compressVersion") String compressVersion,
        /** Active policy id, e.g. ica-policy-v1 */
        @JsonProperty("compressPolicyId") String compressPolicyId
) {
    public static final double COMPRESS_SUGGEST_RATIO = 0.72;
    public static final double COMPRESS_URGENT_RATIO = 0.90;

    public static ContextUsageDto of(int systemTokens, int conversationTokens, int toolsTokens, int limitTokens) {
        int system = Math.max(0, systemTokens);
        int conversation = Math.max(0, conversationTokens);
        int tools = Math.max(0, toolsTokens);
        int messages = system + conversation;
        int used = messages + tools;
        int limit = Math.max(1, limitTokens);
        double percent = Math.min(100.0, Math.round(used * 1000.0 / limit) / 10.0);
        double ratio = used * 1.0 / limit;
        String urgency = (ratio >= 1.0 || ratio >= COMPRESS_URGENT_RATIO)
                ? "urgent"
                : (ratio >= COMPRESS_SUGGEST_RATIO ? "suggest" : "none");
        boolean suggest = !"none".equals(urgency);
        return new ContextUsageDto(
                used, limit, percent, messages, tools, system, conversation,
                suggest, false, urgency, null,
                IntelligentCompressArch.ID,
                IntelligentCompressArch.DISPLAY_NAME_ZH,
                IntelligentCompressArch.VERSION,
                IntelligentCompressArch.POLICY_ID);
    }

    public ContextUsageDto withCompressPolicy(boolean recommended, boolean available, String urgency, String hint) {
        return new ContextUsageDto(
                usedTokens, limitTokens, percent, messageTokens, toolsTokens,
                systemTokens, conversationTokens,
                recommended, available,
                urgency != null ? urgency : compressUrgency,
                hint,
                IntelligentCompressArch.ID,
                IntelligentCompressArch.DISPLAY_NAME_ZH,
                IntelligentCompressArch.VERSION,
                IntelligentCompressArch.POLICY_ID);
    }

    @Deprecated
    public static ContextUsageDto of(int systemTokens, int conversationTokens, int toolsTokens, int limitTokens,
                                     boolean ignoredCompressHint) {
        return of(systemTokens, conversationTokens, toolsTokens, limitTokens);
    }
}