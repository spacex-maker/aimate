package com.openforge.aimate.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.config.SystemConfigService;
import com.openforge.aimate.llm.model.ChatRequest;
import com.openforge.aimate.llm.model.ChatResponse;
import com.openforge.aimate.repository.SystemModelRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.List;

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
 *   If the primary stream fails mid-way, the tokenCallback may have already
 *   been invoked for partial content. If we then pass the same callback to
 *   fallback, fallback will stream from the start again → duplicate tokens.
 *   So on fallback we pass a callback that only accumulates; when fallback
 *   completes we invoke the original callback once with the full content.
 */
@Slf4j
@Component
@EnableConfigurationProperties(LlmProperties.class)
public class LlmRouter {

    private final LlmClient           primaryClient;
    private final LlmClient           fallbackClient;
    private final CircuitBreaker      primaryCb;
    private final CircuitBreaker      fallbackCb;
    private final Retry               primaryRetry;
    private final Retry               fallbackRetry;
    private final SystemModelRepository systemModelRepository;
    private final SystemConfigService   systemConfigService;

    public LlmRouter(HttpClient httpClient,
                     ObjectMapper objectMapper,
                     LlmProperties properties,
                     CircuitBreaker primaryLlmCircuitBreaker,
                     CircuitBreaker fallbackLlmCircuitBreaker,
                     Retry primaryLlmRetry,
                     Retry fallbackLlmRetry,
                     SystemModelRepository systemModelRepository,
                     SystemConfigService systemConfigService) {
        this.systemModelRepository = systemModelRepository;
        this.systemConfigService   = systemConfigService;

        LlmProperties.ProviderConfig primaryCfg  = overrideWithSystemModel(properties.primary());
        LlmProperties.ProviderConfig fallbackCfg = overrideWithSystemModel(properties.fallback());

        this.primaryClient  = new LlmClient(httpClient, objectMapper, primaryCfg);
        this.fallbackClient = new LlmClient(httpClient, objectMapper, fallbackCfg);
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
     * Streaming variant with a single content callback (legacy).
     */
    public ChatResponse streamChat(ChatRequest request, Consumer<String> tokenCallback) {
        return streamChat(request, StreamCallbacks.contentOnly(tokenCallback));
    }

    /**
     * Streaming variant of {@link #chat} — supports separate content and reasoning streams
     * (e.g. DeepSeek reasoning_content). On fallback, only content is replayed once.
     *
     * @param request  standard ChatRequest (stream flag injected internally)
     * @param callbacks onContent = final answer tokens; onReasoning = CoT tokens when provider sends them
     * @return fully assembled ChatResponse after stream ends
     */
    public ChatResponse streamChat(ChatRequest request, StreamCallbacks callbacks) {
        try {
            ChatRequest primaryRequest = overrideModel(request, primaryClient.modelName());
            return executeWithResilience(primaryCb, primaryRetry,
                    () -> primaryClient.streamChat(primaryRequest, callbacks), "primary");
        } catch (Exception primaryException) {
            log.warn("[LlmRouter] Primary stream failed ({}), engaging fallback. Cause: {}",
                    primaryException.getClass().getSimpleName(), primaryException.getMessage());

            ChatRequest fallbackRequest = overrideModel(request, fallbackClient.modelName());
            List<String> fallbackContent = new ArrayList<>();
            StreamCallbacks fallbackAccumulate = new StreamCallbacks(
                    t -> fallbackContent.add(t),
                    t -> {}
            );
            ChatResponse fallbackResponse = executeWithResilience(fallbackCb, fallbackRetry,
                    () -> fallbackClient.streamChat(fallbackRequest, fallbackAccumulate), "fallback");
            String fullContent = String.join("", fallbackContent);
            if (!fullContent.isEmpty()) {
                callbacks.onContent().accept(fullContent);
            }
            return fallbackResponse;
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
     * 根据 system_models + system_config 覆盖系统级 LLM 配置：
     * - 若存在与 config.model 对应的 system_model，则：
     *   - baseUrl 优先用 system_model.base_url，否则保留原 config.baseUrl；
     *   - apiKey 若 system_model.api_key_config_key 在 system_config 中有值，则覆盖为该值；
     *   - model 使用 system_model.model_id。
     * - 若未找到匹配的 system_model，则直接返回原 config。
     */
    private LlmProperties.ProviderConfig overrideWithSystemModel(LlmProperties.ProviderConfig config) {
        if (config == null || config.model() == null || config.model().isBlank()) {
            return config;
        }
        return systemModelRepository.findFirstByModelId(config.model())
                .map(m -> {
                    String baseUrl = (m.getBaseUrl() != null && !m.getBaseUrl().isBlank())
                            ? m.getBaseUrl()
                            : config.baseUrl();

                    String apiKey = config.apiKey();
                    String keyConfigKey = m.getApiKeyConfigKey();
                    if (keyConfigKey != null && !keyConfigKey.isBlank()) {
                        apiKey = systemConfigService.get(keyConfigKey).orElse(apiKey);
                    }

                    String model = m.getModelId() != null && !m.getModelId().isBlank()
                            ? m.getModelId()
                            : config.model();

                    log.info("[LlmRouter] Using SystemModel override for modelId={} -> baseUrl={}, apiKeyKey={}",
                            m.getModelId(), baseUrl, keyConfigKey);

                    return new LlmProperties.ProviderConfig(
                            config.name(),
                            baseUrl,
                            apiKey,
                            model,
                            config.timeoutSeconds()
                    );
                })
                .orElse(config);
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
