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
        /** 仅当占用 ≥72% 时建议压缩（与圆环百分比一致，不因消息条数误报） */
        @JsonProperty("compressRecommended") boolean compressRecommended
) {
    /** 与 ContextCompressService / 前端徽章一致 */
    public static final double COMPRESS_SUGGEST_RATIO = 0.72;

    public static ContextUsageDto of(int systemTokens, int conversationTokens, int toolsTokens, int limitTokens) {
        int system = Math.max(0, systemTokens);
        int conversation = Math.max(0, conversationTokens);
        int tools = Math.max(0, toolsTokens);
        int messages = system + conversation;
        int used = messages + tools;
        int limit = Math.max(1, limitTokens);
        double percent = Math.min(100.0, Math.round(used * 1000.0 / limit) / 10.0);
        boolean suggest = used >= (int) (limit * COMPRESS_SUGGEST_RATIO);
        return new ContextUsageDto(used, limit, percent, messages, tools, system, conversation, suggest);
    }

    /** @deprecated 忽略 messageVolume 开关；建议压缩只看占用比例 */
    @Deprecated
    public static ContextUsageDto of(int systemTokens, int conversationTokens, int toolsTokens, int limitTokens,
                                     boolean ignoredCompressHint) {
        return of(systemTokens, conversationTokens, toolsTokens, limitTokens);
    }
}
