package com.openforge.aimate.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.llm.model.ChatRequest;
import com.openforge.aimate.llm.model.ChatResponse;
import com.openforge.aimate.llm.model.FunctionCallResult;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.llm.model.StreamingChunk;
import com.openforge.aimate.llm.model.ToolCall;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
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
        String requestBody = serialize(request);
        log.debug("[LlmClient:{}] → chat POST body-length={}", config.name(), requestBody.length());

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
     * @param tokenCallback invoked with each non-empty content token
     * @return assembled ChatResponse with complete message
     */
    public ChatResponse streamChat(ChatRequest request, Consumer<String> tokenCallback) {
        if (request == null) {
            throw new LlmException("ChatRequest must not be null for provider [%s]"
                    .formatted(config.name()));
        }

        // 如果上游没有显式指定 model，则自动使用当前 ProviderConfig 的默认模型，
        // 避免出现 model 为空导致像 DeepSeek 返回 “Model Not Exist” 的情况。
        String model = request.model();
        if (model == null || model.isBlank()) {
            model = config.model();
        }

        // DeepSeek 对 tools 支持较为严格，如果历史中残留了孤立的 tool 消息
        // （例如只保留了 tool 结果而裁掉了原始 tool_calls），会直接返回 400。
        // 为了稳妥起见，对 deepseek 提供方，我们在发送前先过滤掉 role = "tool" 的消息。
        var messages = request.messages();
        if (messages != null && "deepseek".equalsIgnoreCase(config.name())) {
            messages = messages.stream()
                    .filter(m -> !"tool".equals(m.role()))
                    .toList();
        }

        ChatRequest effectiveRequest = ChatRequest.builder()
                .model(model)
                .messages(messages)
                .tools(request.tools())
                .toolChoice(request.toolChoice())
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .build();

        String requestBody = serializeWithStream(effectiveRequest);
        log.debug("[LlmClient:{}] → streamChat POST body-length={}", config.name(), requestBody.length());

        HttpResponse<Stream<String>> httpResponse;
        try {
            httpResponse = httpClient.send(
                    buildHttpRequest(requestBody, true),
                    HttpResponse.BodyHandlers.ofLines());
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
            // 对于 DeepSeek 等 OpenAI 兼容接口，错误信息通常在 body 里，收集前几行便于排查
            String bodySnippet = "";
            Stream<String> lines = httpResponse.body();
            if (lines != null) {
                bodySnippet = lines.limit(20).reduce(
                        new StringBuilder(),
                        (sb, line) -> {
                            if (sb.length() > 0) sb.append('\n');
                            if (sb.length() < 2048) {
                                sb.append(line);
                            }
                            return sb;
                        },
                        StringBuilder::append
                ).toString();
            }
            throw new LlmException(
                    "Provider [%s] returned HTTP %d on stream open: %s"
                            .formatted(config.name(), status, bodySnippet));
        }

        return assembleStreamingResponse(httpResponse.body(), tokenCallback);
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
                                                   Consumer<String> tokenCallback) {
        StringBuilder contentBuilder = new StringBuilder();
        // index → {id, type, name, argumentsBuilder}
        Map<Integer, ToolCallAccumulator> toolCallMap = new HashMap<>();
        String responseId    = null;
        String responseModel = null;
        String finishReason  = null;

        for (String line : (Iterable<String>) lines::iterator) {
            if (line.isEmpty() || !line.startsWith(SSE_DATA_PREFIX)) continue;
            if (SSE_DONE.equals(line)) break;

            String json = line.substring(SSE_DATA_PREFIX.length());
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

            // ── Content token ────────────────────────────────────────────────
            if (delta.content() != null && !delta.content().isEmpty()) {
                contentBuilder.append(delta.content());
                tokenCallback.accept(delta.content());
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
        return new ChatResponse(responseId, "chat.completion", null,
                responseModel, List.of(choice), null);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private HttpRequest buildHttpRequest(String body, boolean streaming) {
        return HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                // Streaming responses can take a long time to complete
                .timeout(Duration.ofSeconds(streaming ? config.timeoutSeconds() * 2L : config.timeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private HttpResponse<String> sendBlocking(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Network error calling provider [%s]".formatted(config.name()), e);
        }
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
            throw new LlmException(
                    "Failed to parse response from provider [%s]: %s".formatted(config.name(), body), e);
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new LlmException("Failed to serialize request", e);
        }
    }

    /** Serializes a ChatRequest with "stream": true injected into the JSON. */
    private String serializeWithStream(ChatRequest request) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode node =
                    objectMapper.valueToTree(request);
            node.put("stream", true);
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
