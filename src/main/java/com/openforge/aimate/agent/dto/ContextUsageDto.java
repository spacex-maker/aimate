package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 会话上下文占用估算（对齐 Cursor context ring）：
 * 分母 = 当前模型 context window；分子 = 系统提示 + 对话 + 工具定义（+ 前端当前输入）。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ContextUsageDto(
        @JsonProperty("usedTokens") int usedTokens,
        @JsonProperty("limitTokens") int limitTokens,
        @JsonProperty("percent") double percent,
        /** 兼容旧前端：系统 + 对话合计 */
        @JsonProperty("messageTokens") int messageTokens,
        @JsonProperty("toolsTokens") int toolsTokens,
        @JsonProperty("systemTokens") int systemTokens,
        @JsonProperty("conversationTokens") int conversationTokens,
        /** 占用 ≥72% 或消息量足够时建议用户压缩 */
        @JsonProperty("compressRecommended") boolean compressRecommended
) {
    /** 与 ContextCompressService / 前端徽章一致 */
    public static final double COMPRESS_SUGGEST_RATIO = 0.72;

    public static ContextUsageDto of(int systemTokens, int conversationTokens, int toolsTokens, int limitTokens,
                                     boolean compressRecommended) {
        int system = Math.max(0, systemTokens);
        int conversation = Math.max(0, conversationTokens);
        int tools = Math.max(0, toolsTokens);
        int messages = system + conversation;
        int used = messages + tools;
        int limit = Math.max(1, limitTokens);
        double percent = Math.min(100.0, Math.round(used * 1000.0 / limit) / 10.0);
        boolean suggest = compressRecommended
                || used >= (int) (limit * COMPRESS_SUGGEST_RATIO);
        return new ContextUsageDto(used, limit, percent, messages, tools, system, conversation, suggest);
    }

    /** 兼容旧调用 */
    public static ContextUsageDto of(int systemTokens, int conversationTokens, int toolsTokens, int limitTokens) {
        return of(systemTokens, conversationTokens, toolsTokens, limitTokens, false);
    }
}
