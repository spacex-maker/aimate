package com.openforge.aimate.llm;

import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.llm.model.ToolCall;

import java.util.List;

/**
 * 轻量级 Token 数估算器，用于在 provider 不返回 usage 时自行估算 token 数。
 *
 * <p>经验近似（贴近常见 BPE / DeepSeek 等中英混合表现）：
 * <ul>
 *   <li>CJK / 全角字符 ≈ 1 token / 字</li>
 *   <li>其余（拉丁等）≈ 4 个 code point ≈ 1 token</li>
 *   <li>每条消息额外 +4（role / 分隔开销）</li>
 * </ul>
 * 估算用于上下文占用圆环，非精确计费。
 */
public final class TokenCounter {

    /** 每条消息的结构开销粗估 */
    private static final int MESSAGE_OVERHEAD = 4;

    private TokenCounter() {}

    /** 按消息列表估算 prompt token 数。 */
    public static int estimateTokensForMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        int total = 0;
        for (Message m : messages) {
            if (m == null) continue;
            total += MESSAGE_OVERHEAD;
            if (m.content() != null) {
                total += estimateTokens(m.content());
            }
            List<ToolCall> toolCalls = m.toolCalls();
            if (toolCalls != null) {
                for (ToolCall tc : toolCalls) {
                    if (tc == null || tc.function() == null) continue;
                    String name = tc.function().name();
                    if (name != null) total += estimateTokens(name);
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
        int tokens = 0;
        int latinBudget = 0;
        for (int i = 0; i < trimmed.length(); ) {
            int cp = trimmed.codePointAt(i);
            i += Character.charCount(cp);
            if (isDenseChar(cp)) {
                if (latinBudget > 0) {
                    tokens += (latinBudget + 3) / 4;
                    latinBudget = 0;
                }
                tokens += 1;
            } else {
                latinBudget += 1;
            }
        }
        if (latinBudget > 0) {
            tokens += (latinBudget + 3) / 4;
        }
        return Math.max(tokens, 1);
    }

    /** CJK / 全角等通常接近 1 token / 字 */
    private static boolean isDenseChar(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0x3400 && cp <= 0x4DBF)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0x3000 && cp <= 0x303F)
                || (cp >= 0xFF00 && cp <= 0xFFEF)
                || (cp >= 0x3040 && cp <= 0x30FF)
                || (cp >= 0xAC00 && cp <= 0xD7AF);
    }
}
