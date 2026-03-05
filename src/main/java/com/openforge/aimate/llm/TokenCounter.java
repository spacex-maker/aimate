package com.openforge.aimate.llm;

import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.llm.model.ToolCall;

import java.util.List;

/**
 * 轻量级 Token 数估算器，用于在 provider 不返回 usage 时自行估算 token 数。
 *
 * 说明：
 * - 这里不尝试精确复刻各家 tokenizer（如 cl100k_base），而是采用经验近似：
 *   - 按字符数估算：平均 ~3 个字符 ≈ 1 个 token（中英混合场景下比较保守）
 *   - 工具调用的 JSON 参数也计入 token。
 * - 估算结果可用于成本/用量大致分析，而非精确计费。
 */
public final class TokenCounter {

    private TokenCounter() {}

    /** 按消息列表估算 prompt token 数。 */
    public static int estimateTokensForMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        int total = 0;
        for (Message m : messages) {
            if (m == null) continue;
            if (m.content() != null) {
                total += estimateTokens(m.content());
            }
            List<ToolCall> toolCalls = m.toolCalls();
            if (toolCalls != null) {
                for (ToolCall tc : toolCalls) {
                    if (tc == null || tc.function() == null) continue;
                    String args = tc.function().arguments();
                    if (args != null) total += estimateTokens(args);
                }
            }
        }
        return total;
    }

    /** 按字符串估算 token 数。 */
    public static int estimateTokens(String text) {
        if (text == null) return 0;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return 0;
        // 使用 codePoint 数量，兼容多字节字符；经验上中英混合场景下 ~3 个 codePoint ≈ 1 个 token
        int codePoints = trimmed.codePointCount(0, trimmed.length());
        int tokens = (int) Math.round(codePoints / 3.0);
        return Math.max(tokens, 1);
    }
}

