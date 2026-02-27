package com.openforge.aimate.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.llm.model.ChatRequest;
import com.openforge.aimate.llm.model.ChatResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * High-availability LLM request router.
 *
 * Call graph (both chat and streamChat):
 *
 *   chat(request)  /  streamChat(request, tokenCallback)
 *     └─ primaryCircuitBreaker + primaryRetry
 *           └─ primaryLlmClient.{chat|streamChat}(request)
 *                 ↓ (on CallNotPermittedException or any exception)
 *     └─ fallbackCircuitBreaker + fallbackRetry
 *           └─ fallbackLlmClient.{chat|streamChat}(request)
 *
 * The "thinking never stops" guarantee:
 *   As long as at least one provider is reachable the Agent loop
 *   continues without human intervention.
 *
 * Streaming note:
 *   Circuit breakers trip on the HTTP connection phase.  If the primary
 *   provider opens the stream but then drops mid-way, the exception bubbles
 *   out of assembleStreamingResponse() and falls through to the fallback.
 *   The tokenCallback may have already fired for partial content; the Agent
 *   loop should treat this as a partial thought and continue from fallback.
 */
@Slf4j
@Component
@EnableConfigurationProperties(LlmProperties.class)
public class LlmRouter {

    private final LlmClient     primaryClient;
    private final LlmClient     fallbackClient;
    private final CircuitBreaker primaryCb;
    private final CircuitBreaker fallbackCb;
    private final Retry          primaryRetry;
    private final Retry          fallbackRetry;

    public LlmRouter(HttpClient httpClient,
                     ObjectMapper objectMapper,
                     LlmProperties properties,
                     CircuitBreaker primaryLlmCircuitBreaker,
                     CircuitBreaker fallbackLlmCircuitBreaker,
                     Retry primaryLlmRetry,
                     Retry fallbackLlmRetry) {
        this.primaryClient  = new LlmClient(httpClient, objectMapper, properties.primary());
        this.fallbackClient = new LlmClient(httpClient, objectMapper, properties.fallback());
        this.primaryCb      = primaryLlmCircuitBreaker;
        this.fallbackCb     = fallbackLlmCircuitBreaker;
        this.primaryRetry   = primaryLlmRetry;
        this.fallbackRetry  = fallbackLlmRetry;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Route a chat request through primary → fallback with full resilience.
     *
     * The model field in ChatRequest is overridden by the provider's own
     * configured model name, so callers only need to pass the message list.
     */
    public ChatResponse chat(ChatRequest request) {
        try {
            ChatRequest primaryRequest = overrideModel(request, primaryClient.modelName());
            return executeWithResilience(primaryCb, primaryRetry,
                    () -> primaryClient.chat(primaryRequest), "primary");
        } catch (Exception primaryException) {
            log.warn("[LlmRouter] Primary provider failed ({}), engaging fallback. Cause: {}",
                    primaryException.getClass().getSimpleName(), primaryException.getMessage());

            ChatRequest fallbackRequest = overrideModel(request, fallbackClient.modelName());
            return executeWithResilience(fallbackCb, fallbackRetry,
                    () -> fallbackClient.chat(fallbackRequest), "fallback");
        }
    }

    /**
     * Streaming variant of {@link #chat} — identical resilience wrapping,
     * but delegates to {@link LlmClient#streamChat} so each token is
     * forwarded to the WebSocket callback in real-time.
     *
     * @param request        standard ChatRequest (stream flag injected internally)
     * @param tokenCallback  called once per content token on the current virtual thread
     * @return fully assembled ChatResponse after stream ends
     */
    public ChatResponse streamChat(ChatRequest request, Consumer<String> tokenCallback) {
        try {
            ChatRequest primaryRequest = overrideModel(request, primaryClient.modelName());
            return executeWithResilience(primaryCb, primaryRetry,
                    () -> primaryClient.streamChat(primaryRequest, tokenCallback), "primary");
        } catch (Exception primaryException) {
            log.warn("[LlmRouter] Primary stream failed ({}), engaging fallback. Cause: {}",
                    primaryException.getClass().getSimpleName(), primaryException.getMessage());

            ChatRequest fallbackRequest = overrideModel(request, fallbackClient.modelName());
            return executeWithResilience(fallbackCb, fallbackRetry,
                    () -> fallbackClient.streamChat(fallbackRequest, tokenCallback), "fallback");
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Decorates a supplier with circuit-breaker + retry, then executes it.
     * Fully programmatic — no AOP proxies, no annotations.
     */
    private ChatResponse executeWithResilience(CircuitBreaker cb,
                                               Retry retry,
                                               Supplier<ChatResponse> call,
                                               String label) {
        Supplier<ChatResponse> decorated =
                CircuitBreaker.decorateSupplier(cb,
                        Retry.decorateSupplier(retry, call));
        try {
            return decorated.get();
        } catch (Exception e) {
            throw new LlmClient.LlmException(
                    "[LlmRouter] %s provider ultimately failed: %s".formatted(label, e.getMessage()), e);
        }
    }

    /**
     * Copies a ChatRequest, substituting the model name.
     * Records are immutable, so we reconstruct via the builder.
     */
    private ChatRequest overrideModel(ChatRequest original, String modelName) {
        return ChatRequest.builder()
                .model(modelName)
                .messages(original.messages())
                .tools(original.tools())
                .toolChoice(original.toolChoice())
                .temperature(original.temperature())
                .maxTokens(original.maxTokens())
                .build();
    }
}
