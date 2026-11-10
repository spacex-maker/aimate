package com.openforge.aimate.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.llm.model.ChatRequest;
import com.openforge.aimate.llm.model.ChatResponse;
import com.openforge.aimate.llm.model.FunctionCallResult;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.llm.model.StreamingChunk;
import com.openforge.aimate.llm.model.ToolCall;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Zero-dependency, stateless LLM HTTP client.
 *
 * Two modes of operation:
 *
 *  chat()        — synchronous, waits for full response.
 *                  Used for planner calls where we want the whole plan at once.
 *
 *  streamChat()  — streaming SSE, calls tokenCallback per token.
 *                  Used for the Agent's main reasoning loop so that each
 *                  thinking token is forwarded to WebSocket in real-time.
 *                  Returns the fully assembled ChatResponse when the stream ends.
 *
 * Both methods are intentionally synchronous.  On a virtual thread, the
 * blocking I/O wait costs nothing — no CompletableFuture needed.
 */
@Slf4j
public class LlmClient {

    private static final String SSE_DATA_PREFIX = "data: ";
    private static final String SSE_DONE        = "data: [DONE]";

    private final HttpClient   httpClient;
    private final ObjectMapper objectMapper;
    private final LlmProperties.ProviderConfig config;

    public LlmClient(HttpClient httpClient,
                     ObjectMapper objectMapper,
                     LlmProperties.ProviderConfig config) {
        this.httpClient   = httpClient;
        this.objectMapper = objectMapper;
        this.config       = config;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Blocking (non-streaming) chat completion.
     * Use for planner calls, structured-output calls, or any case where
     * token-by-token delivery is not needed.
     */
    public ChatResponse chat(ChatRequest request) {
        ChatRequest effectiveRequest = prepareRequest(request);
        String requestBody = serialize(effectiveRequest);
        log.debug("[LlmClient:{}] → chat POST body-length={}", config.name(), requestBody.length());

        // 便于排查：打印当前调用使用的 URL / model / key（仅显示 key 头尾，避免泄露全部秘钥）
        log.info("[LlmClient:{}] chat endpoint={} model={} key={}",
                config.name(),
                config.baseUrl() + "/chat/completions",
                effectiveRequest.model(),
                maskKey(config.apiKey()));

        HttpResponse<String> httpResponse = sendBlocking(
                buildHttpRequest(requestBody, false));

        return parseFullResponse(httpResponse);
    }

    /**
     * Streaming chat completion via SSE.
     *
     * For every content token the LLM emits, {@code tokenCallback} is invoked
     * synchronously on the current virtual thread.  The caller (AgentLoopService)
     * typically publishes the token to WebSocket inside that callback.
     *
     * Tool-call arguments are also streamed and accumulated internally.
     * The assembled ChatResponse (with full tool_calls list) is returned
     * when the stream ends, so the loop can act on tool calls exactly as
     * it would with a non-streaming response.
     *
     * @param request       ChatRequest (stream flag is forced to true internally)
     * @param tokenCallback invoked with each non-empty content token (legacy single-stream)
     * @return assembled ChatResponse with complete message
     */
    public ChatResponse streamChat(ChatRequest request, Consumer<String> tokenCallback) {
        return streamChat(request, StreamCallbacks.contentOnly(tokenCallback));
    }

    /**
     * Streaming chat with separate content and reasoning callbacks.
     * When the provider sends {@code delta.reasoning_content} (e.g. DeepSeek thinking mode),
     * {@code callbacks.onReasoning()} is invoked; {@code delta.content} goes to {@code callbacks.onContent()}.
     */
    public ChatResponse streamChat(ChatRequest request, StreamCallbacks callbacks) {
        if (request == null) {
            throw new LlmException("ChatRequest must not be null for provider [%s]"
                    .formatted(config.name()));
        }

        // Send full context including tool results. An assistant message with tool_calls
        // must be followed by tool messages for each tool_call_id (OpenAI/DeepSeek spec).
        ChatRequest effectiveRequest = prepareRequest(request);
        String model = effectiveRequest.model();

        String requestBody = serializeWithStream(effectiveRequest);
        log.debug("[LlmClient:{}] → streamChat POST body-length={}", config.name(), requestBody.length());

        // 便于排查：打印当前调用使用的 URL / model / key（仅显示 key 头尾，避免泄露全部秘钥）
        log.info("[LlmClient:{}] stream endpoint={} model={} key={}",
                config.name(),
                config.baseUrl() + "/chat/completions",
                model,
                maskKey(config.apiKey()));

        // 预估 prompt token 数，供组装 ChatResponse.usage 使用（provider 多数在流式模式下不返回 usage）
        int estimatedPromptTokens = TokenCounter.estimateTokensForMessages(effectiveRequest.messages());

        HttpResponse<InputStream> httpResponse;
        try {
            httpResponse = httpClient.send(
                    buildHttpRequest(requestBody, true),
                    HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Network error (streaming) calling provider [%s]"
                    .formatted(config.name()), e);
        }

        int status = httpResponse.statusCode();
        if (status == 429) {
            throw new LlmRateLimitException(
                    "Rate-limited by provider [%s].".formatted(config.name()));
        }
        if (status < 200 || status >= 300) {
            String bodyText = "";
            try (InputStream errIn = httpResponse.body()) {
                if (errIn != null) {
                    bodyText = new String(errIn.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException ignored) {
                // keep empty bodyText
            }
            throw new LlmException(
                    "Provider [%s] returned HTTP %d on stream open: %s"
                            .formatted(config.name(), status, bodyText));
        }

        ChatResponse streamed;
        try (InputStream in = httpResponse.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            streamed = assembleStreamingResponse(reader.lines(), callbacks, estimatedPromptTokens);
        } catch (IOException e) {
            throw new LlmException("Network error reading stream from provider [%s]"
                    .formatted(config.name()), e);
        }

        Message msg = streamed.firstMessage();
        boolean emptyContent = msg == null
                || msg.content() == null
                || msg.content().isBlank();
        boolean noTools = msg == null
                || msg.toolCalls() == null
                || msg.toolCalls().isEmpty();
        if (emptyContent && noTools) {
            log.warn("[LlmClient:{}] stream returned empty content/tool_calls; falling back to non-stream chat. model={} finish={} id={}",
                    config.name(), model,
                    streamed.choices() != null && !streamed.choices().isEmpty()
                            ? streamed.choices().get(0).finishReason() : null,
                    streamed.id());
            ChatResponse fallback = chat(effectiveRequest);
            Message fb = fallback.firstMessage();
            if (fb != null) {
                if (fb.reasoningContent() != null && !fb.reasoningContent().isBlank()) {
                    callbacks.onReasoning().accept(fb.reasoningContent());
                }
                if (fb.content() != null && !fb.content().isBlank()) {
                    callbacks.onContent().accept(fb.content());
                } else {
                    log.warn("[LlmClient:{}] non-stream fallback also empty. model={} finish={}",
                            config.name(), model,
                            fallback.choices() != null && !fallback.choices().isEmpty()
                                    ? fallback.choices().get(0).finishReason() : null);
                }
            }
            return fallback;
        }
        return streamed;
    }

    /** The model name configured for this provider (e.g. "gpt-4o"). */
    public String modelName() {
        return config.model();
    }

    // ── Streaming assembly ───────────────────────────────────────────────────

    /**
     * Reads the SSE line stream, calls tokenCallback for content deltas,
     * and assembles a ChatResponse that mirrors the non-streaming format.
     *
     * Tool-call assembly:
     *   The LLM streams tool-call fragments across many chunks.
     *   We accumulate name + arguments per tool-call index and build
     *   complete ToolCall objects at the end.
     */
    private ChatResponse assembleStreamingResponse(Stream<String> lines,
                                                   StreamCallbacks callbacks,
                                                   int estimatedPromptTokens) {
        StringBuilder contentBuilder = new StringBuilder();
        // index → {id, type, name, argumentsBuilder}
        Map<Integer, ToolCallAccumulator> toolCallMap = new HashMap<>();
        String responseId    = null;
        String responseModel = null;
        String finishReason  = null;

        for (String line : (Iterable<String>) lines::iterator) {
            if (line == null) continue;
            line = line.strip();
            if (line.isEmpty() || !line.startsWith("data:")) continue;
            String payload = line.substring(5).strip();
            if ("[DONE]".equals(payload)) break;
            if (payload.isEmpty()) continue;

            String json = payload;
            StreamingChunk chunk;
            try {
                chunk = objectMapper.readValue(json, StreamingChunk.class);
            } catch (JsonProcessingException e) {
                log.warn("[LlmClient:{}] Failed to parse SSE chunk: {}", config.name(), json);
                continue;
            }

            if (responseId == null)    responseId    = chunk.id();
            if (responseModel == null) responseModel = chunk.model();

            if (chunk.choices() == null || chunk.choices().isEmpty()) continue;

            StreamingChunk.ChunkChoice choice = chunk.choices().get(0);
            if (choice.finishReason() != null) finishReason = choice.finishReason();

            StreamingChunk.DeltaMessage delta = choice.delta();
            if (delta == null) continue;

            // ── Reasoning token (DeepSeek reasoning_content / Ollama reasoning|thinking) ──
            String reasoningDelta = firstNonBlank(
                    delta.reasoningContent(), delta.reasoning(), delta.thinking());
            if (reasoningDelta != null) {
                callbacks.onReasoning().accept(reasoningDelta);
            }

            // ── Content token (final answer) ──────────────────────────────────
            if (delta.content() != null && !delta.content().isEmpty()) {
                String contentDelta = delta.content();
                if (contentBuilder.length() > 0 && contentDelta.startsWith(contentBuilder.toString())) {
                    // 部分接口返回累积内容而非增量，只追加新增部分并只回调新部分，避免重复
                    String newPart = contentDelta.substring(contentBuilder.length());
                    contentBuilder.setLength(0);
                    contentBuilder.append(contentDelta);
                    if (!newPart.isEmpty()) callbacks.onContent().accept(newPart);
                } else {
                    contentBuilder.append(contentDelta);
                    callbacks.onContent().accept(contentDelta);
                }
            }

            // ── Tool-call delta ──────────────────────────────────────────────
            if (delta.toolCalls() != null) {
                for (StreamingChunk.ToolCallDelta tcDelta : delta.toolCalls()) {
                    int idx = tcDelta.index() != null ? tcDelta.index() : 0;
                    ToolCallAccumulator acc = toolCallMap.computeIfAbsent(idx,
                            i -> new ToolCallAccumulator());
                    if (tcDelta.id()   != null) acc.id   = tcDelta.id();
                    if (tcDelta.type() != null) acc.type = tcDelta.type();
                    if (tcDelta.function() != null) {
                        if (tcDelta.function().name()      != null) acc.name = tcDelta.function().name();
                        if (tcDelta.function().arguments() != null) acc.argsBuilder.append(tcDelta.function().arguments());
                    }
                }
            }
        }

        // ── Build final ChatResponse ─────────────────────────────────────────
        List<ToolCall> toolCalls = null;
        if (!toolCallMap.isEmpty()) {
            toolCalls = new ArrayList<>();
            for (var entry : toolCallMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                ToolCallAccumulator acc = entry.getValue();
                toolCalls.add(new ToolCall(acc.id, acc.type,
                        new FunctionCallResult(acc.name, acc.argsBuilder.toString())));
            }
        }

        Message assistantMessage = Message.builder()
                .role("assistant")
                .content(contentBuilder.isEmpty() ? null : contentBuilder.toString())
                .toolCalls(toolCalls)
                .build();

        ChatResponse.Choice choice = new ChatResponse.Choice(0, assistantMessage, finishReason);

        // 使用内容长度估算 completion tokens，结合上游估算的 prompt tokens，构造 usage 供日志使用
        int completionTokens = TokenCounter.estimateTokens(assistantMessage.content());
        int promptTokens = Math.max(0, estimatedPromptTokens);
        int totalTokens = promptTokens + completionTokens;
        ChatResponse.Usage usage = new ChatResponse.Usage(promptTokens, completionTokens, totalTokens);

        return new ChatResponse(responseId, "chat.completion", null,
                responseModel, List.of(choice), usage);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * 统一补全 model、透传 think、清洗 Ollama 不耐受的空 assistant，并对本地兼容端点限幅 max_tokens。
     */
    private ChatRequest prepareRequest(ChatRequest request) {
        if (request == null) {
            throw new LlmException("ChatRequest must not be null for provider [%s]"
                    .formatted(config.name()));
        }
        String model = request.model();
        if (model == null || model.isBlank()) {
            model = config.model();
        }
        Integer maxTokens = request.maxTokens();
        java.util.List<Message> messages = sanitizeMessages(request.messages());
        if (looksLikeLocalOllamaCompat()) {
            int promptEst = TokenCounter.estimateTokensForMessages(messages);
            // Prefer caller-provided maxTokens; still hard-cap so prompt+completion stays sane.
            int hardCap = Math.max(256, 8_192 - Math.min(promptEst, 7_500));
            if (maxTokens == null || maxTokens > hardCap) {
                log.debug("[LlmClient:{}] cap max_tokens {} → {} (prompt≈{} local/Aimodel)",
                        config.name(), maxTokens, hardCap, promptEst);
                maxTokens = hardCap;
            }
        }
        return ChatRequest.builder()
                .model(model)
                .messages(messages)
                .tools(request.tools())
                .toolChoice(request.toolChoice())
                .temperature(request.temperature())
                .maxTokens(maxTokens)
                .thinking(request.thinking())
                .think(request.think())
                .build();
    }

    /** Aimodel / Qwen / Ollama 等对 null content、过大 max_tokens 更敏感。 */
    private boolean looksLikeLocalOllamaCompat() {
        String name = config.name() != null ? config.name().toLowerCase() : "";
        String url = config.baseUrl() != null ? config.baseUrl().toLowerCase() : "";
        String model = config.model() != null ? config.model().toLowerCase() : "";
        return name.contains("aimodel") || name.contains("qwen") || name.contains("ollama")
                || url.contains("aimodel") || url.contains(":11434") || url.contains(":11435")
                || model.contains("qwen");
    }

    /**
     * 去掉无 tool_calls 的空 assistant（含「(无内容)」占位），避免 Ollama 400；
     * 有 tool_calls 时把 null content 归一成 ""。
     */
    private List<Message> sanitizeMessages(List<Message> messages) {
        if (messages == null) return null;
        List<Message> out = new ArrayList<>(messages.size());
        for (Message m : messages) {
            if (m == null) continue;
            boolean hasTools = m.toolCalls() != null && !m.toolCalls().isEmpty();
            String c = m.content();
            boolean emptyText = c == null || c.isBlank()
                    || "(无内容)".equals(c) || "(无文字回复)".equals(c);
            if ("assistant".equals(m.role()) && !hasTools && emptyText) {
                continue;
            }
            if ("assistant".equals(m.role()) && hasTools && c == null) {
                out.add(Message.builder()
                        .role(m.role())
                        .content("")
                        .toolCalls(m.toolCalls())
                        .toolCallId(m.toolCallId())
                        .reasoningContent(m.reasoningContent())
                        .imageDataUrls(m.imageDataUrls())
                        .build());
                continue;
            }
            out.add(m);
        }
        return out;
    }

    private HttpRequest buildHttpRequest(String body, boolean streaming) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .timeout(Duration.ofSeconds(streaming ? config.timeoutSeconds() * 2L : config.timeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (streaming) {
            b.header("Accept", "text/event-stream");
        } else {
            b.header("Accept", "application/json");
        }
        return b.build();
    }

    private HttpResponse<String> sendBlocking(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Network error calling provider [%s]".formatted(config.name()), e);
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    /** 日志中安全展示部分秘钥：前 4 + '***' + 后 4，空则标记为 <null>。 */
    private String maskKey(String key) {
        if (key == null || key.isBlank()) return "<null>";
        String trimmed = key.trim();
        if (trimmed.length() <= 8) return "****" + trimmed.length();
        return trimmed.substring(0, 4) + "***" + trimmed.substring(trimmed.length() - 4);
    }


    private ChatResponse parseFullResponse(HttpResponse<String> response) {
        int    status = response.statusCode();
        String body   = response.body();
        log.debug("[LlmClient:{}] ← HTTP {} body-length={}", config.name(), status,
                body == null ? 0 : body.length());

        if (status == 429) throw new LlmRateLimitException(
                "Rate-limited by provider [%s].".formatted(config.name()));
        if (status < 200 || status >= 300) throw new LlmException(
                "Provider [%s] returned HTTP %d: %s".formatted(config.name(), status, body));

        try {
            return objectMapper.readValue(body, ChatResponse.class);
        } catch (JsonProcessingException e) {
            // DeepSeek / OpenAI 兼容接口可能会在 usage 等结构中加入额外字段或结构变化，
            // 导致直接反序列化到 ChatResponse 失败。这里做一次兜底解析，只关心
            //  id / model / choices[0].message 这些我们真正需要的字段。
            log.warn("[LlmClient:{}] Failed to parse ChatResponse normally, falling back to lenient parsing: {}",
                    config.name(), e.getMessage());
            try {
                JsonNode root = objectMapper.readTree(body);

                String id      = root.path("id").asText(null);
                String object  = root.path("object").asText(null);
                Long   created = root.hasNonNull("created") ? root.get("created").asLong() : null;
                String model   = root.path("model").asText(null);

                List<ChatResponse.Choice> choices = List.of();
                JsonNode choicesNode = root.path("choices");
                if (choicesNode.isArray() && !choicesNode.isEmpty()) {
                    JsonNode first = choicesNode.get(0);
                    int index = first.path("index").asInt(0);
                    String finishReason = first.path("finish_reason").asText(null);
                    JsonNode messageNode = first.path("message");
                    Message message = null;
                    if (!messageNode.isMissingNode() && !messageNode.isNull()) {
                        message = objectMapper.treeToValue(messageNode, Message.class);
                    }
                    choices = List.of(new ChatResponse.Choice(index, message, finishReason));
                }

                // usage 结构经常变化，兜底模式下直接置空即可。
                ChatResponse.Usage usage = null;

                return new ChatResponse(id, object, created, model, choices, usage);
            } catch (Exception fallbackEx) {
                throw new LlmException(
                        "Failed to parse response from provider [%s]: %s".formatted(config.name(), body),
                        fallbackEx);
            }
        }
    }

    private String serialize(Object obj) {
        try {
            if (obj instanceof ChatRequest request) {
                com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.valueToTree(request);
                applyImageContent(node, request);
                return objectMapper.writeValueAsString(node);
            }
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new LlmException("Failed to serialize request", e);
        }
    }

    /** 用户消息带图片时，把 content 写成 OpenAI / DeepSeek 的 text + image_url 数组。 */
    private void applyImageContent(com.fasterxml.jackson.databind.node.ObjectNode requestNode, ChatRequest request) {
        if (request.messages() == null || !requestNode.get("messages").isArray()) return;
        com.fasterxml.jackson.databind.node.ArrayNode messages =
                (com.fasterxml.jackson.databind.node.ArrayNode) requestNode.get("messages");
        int n = Math.min(messages.size(), request.messages().size());
        for (int i = 0; i < n; i++) {
            Message src = request.messages().get(i);
            if (src.imageDataUrls() == null || src.imageDataUrls().isEmpty() || !"user".equals(src.role())) continue;
            com.fasterxml.jackson.databind.node.ArrayNode content = objectMapper.createArrayNode();
            if (src.content() != null && !src.content().isBlank()) {
                content.addObject().put("type", "text").put("text", src.content());
            }
            for (String url : src.imageDataUrls()) {
                content.addObject().put("type", "image_url").putObject("image_url").put("url", url);
            }
            ((com.fasterxml.jackson.databind.node.ObjectNode) messages.get(i)).set("content", content);
        }
    }

    /** Serializes a ChatRequest with "stream": true injected into the JSON. */
    private String serializeWithStream(ChatRequest request) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode node =
                    objectMapper.valueToTree(request);
            node.put("stream", true);
            applyImageContent(node, request);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new LlmException("Failed to serialize streaming request", e);
        }
    }

    // ── Accumulator for streaming tool-call assembly ─────────────────────────

    private static class ToolCallAccumulator {
        String        id   = "";
        String        type = "function";
        String        name = "";
        StringBuilder argsBuilder = new StringBuilder();
    }

    // ── Exception types ──────────────────────────────────────────────────────

    public static class LlmException extends RuntimeException {
        public LlmException(String message) { super(message); }
        public LlmException(String message, Throwable cause) { super(message, cause); }
    }

    public static class LlmRateLimitException extends LlmException {
        public LlmRateLimitException(String message) { super(message); }
    }
}
