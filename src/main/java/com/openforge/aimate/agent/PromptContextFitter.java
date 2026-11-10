package com.openforge.aimate.agent;

import com.openforge.aimate.llm.TokenCounter;
import com.openforge.aimate.llm.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fit chat messages into a model context window before calling the provider.
 * Prevents llama.cpp / Aimodel {@code exceed_context_size_error} when switching
 * from a large-window model (e.g. 128k) to a small one (e.g. Flash-Next 8k).
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
        if (isSmallWindowModel(modelId) || ctx <= 16_384) {
            return Math.min(2_048, Math.max(512, ctx / 3));
        }
        return 16_384;
    }

    public static boolean isSmallWindowModel(String modelId) {
        if (modelId == null || modelId.isBlank()) return false;
        String id = modelId.toLowerCase(Locale.ROOT);
        return id.contains("flash-next") || id.contains("qwen3.8-flash");
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