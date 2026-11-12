package com.openforge.aimate.agent;

import com.openforge.aimate.llm.TokenCounter;
import com.openforge.aimate.llm.model.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Fit chat messages into a model context window before calling the provider.
 * Prevents llama.cpp / Aimodel {@code exceed_context_size_error} when switching
 * from a large-window model (e.g. 128k) to a smaller one (uses each model's context_window_tokens).
 */
public final class PromptContextFitter {

    private PromptContextFitter() {}

    public record FitResult(List<Message> messages, int promptTokens, int maxTokens, int droppedMessages) {}

    /**
     * @param contextWindow model n_ctx (e.g. 8192)
     * @param toolsReserve  estimated tool-schema tokens
     * @param preferredMaxTokens desired completion budget
     */
    public static FitResult fit(
            List<Message> messages,
            int contextWindow,
            int toolsReserve,
            int preferredMaxTokens
    ) {
        List<Message> src = messages == null ? List.of() : new ArrayList<>(messages);
        int ctx = Math.max(1024, contextWindow);
        int tools = Math.max(0, toolsReserve);
        int preferred = Math.max(256, preferredMaxTokens);

        // Keep room for completion; small windows need a larger share for the reply.
        int completionFloor = Math.min(preferred, Math.max(256, ctx / 4));
        int promptBudget = Math.max(512, ctx - tools - completionFloor);

        List<Message> prefix = new ArrayList<>();
        List<Message> rest = new ArrayList<>();
        int i = 0;
        while (i < src.size() && src.get(i) != null && "system".equalsIgnoreCase(src.get(i).role())) {
            prefix.add(src.get(i++));
        }
        while (i < src.size()) {
            rest.add(src.get(i++));
        }

        int dropped = 0;
        while (!rest.isEmpty() && estimate(prefix, rest) > promptBudget) {
            if (rest.size() <= 1) {
                break; // always keep the latest turn if possible
            }
            rest.remove(0);
            dropped++;
            // Avoid starting mid tool-chain (tool / empty assistant with tool_calls)
            while (!rest.isEmpty() && isUnsafeHead(rest.get(0)) && rest.size() > 1) {
                rest.remove(0);
                dropped++;
            }
        }

        List<Message> fitted = new ArrayList<>(prefix.size() + rest.size());
        fitted.addAll(prefix);
        fitted.addAll(rest);

        int promptTokens = estimate(prefix, rest);
        int remaining = Math.max(256, ctx - tools - promptTokens);
        int maxTokens = Math.min(preferred, remaining);
        // Never request more completion tokens than the hard context allows.
        maxTokens = Math.min(maxTokens, Math.max(256, ctx - 1));

        return new FitResult(fitted, promptTokens, maxTokens, dropped);
    }

    public static int estimateToolsReserve(int toolCount) {
        if (toolCount <= 0) return 64;
        return 1_800 + toolCount * 80;
    }

    public static int preferredMaxTokensForModel(String modelId, int contextWindow) {
        int ctx = Math.max(1024, contextWindow);
        // 小窗口给回复留更大比例；大窗口按上下文 1/4 给补全预算（上限 32k），最终仍由 fit() 按剩余空间收紧
        if (ctx <= 16_384) {
            return Math.min(2_048, Math.max(512, ctx / 3));
        }
        return Math.min(32_768, Math.max(4_096, ctx / 4));
    }

    private static boolean isUnsafeHead(Message m) {
        if (m == null) return true;
        if ("tool".equalsIgnoreCase(m.role())) return true;
        if ("assistant".equalsIgnoreCase(m.role())
                && (m.content() == null || m.content().isBlank())
                && m.toolCalls() != null && !m.toolCalls().isEmpty()) {
            return true;
        }
        return false;
    }

    private static int estimate(List<Message> prefix, List<Message> rest) {
        List<Message> all = new ArrayList<>(prefix.size() + rest.size());
        all.addAll(prefix);
        all.addAll(rest);
        return TokenCounter.estimateTokensForMessages(all);
    }
}