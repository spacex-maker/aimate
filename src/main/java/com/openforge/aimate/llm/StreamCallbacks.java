package com.openforge.aimate.llm;

import java.util.function.Consumer;

/**
 * Callbacks for streaming chat when the provider can send two streams:
 * <ul>
 *   <li>{@link #onContent(String)} — final answer tokens (delta.content)</li>
 *   <li>{@link #onReasoning(String)} — chain-of-thought / reasoning tokens (e.g. DeepSeek delta.reasoning_content)</li>
 * </ul>
 * When the provider does not send reasoning (e.g. standard OpenAI), only {@code onContent} is invoked.
 */
public record StreamCallbacks(
        Consumer<String> onContent,
        Consumer<String> onReasoning
) {
    /** Only content stream; reasoning callback is a no-op. Use for providers without reasoning_content. */
    public static StreamCallbacks contentOnly(Consumer<String> onContent) {
        return new StreamCallbacks(onContent, t -> {});
    }
}
