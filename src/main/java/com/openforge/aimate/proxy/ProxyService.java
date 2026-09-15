package com.openforge.aimate.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.apikey.UserApiKeyResolver;
import com.openforge.aimate.domain.LlmCallLog;
import com.openforge.aimate.embedding.UserEmbeddingResolver;
import com.openforge.aimate.llm.LlmCallLogService;
import com.openforge.aimate.llm.LlmClient;
import com.openforge.aimate.llm.LlmProperties;
import com.openforge.aimate.llm.LlmRouter;
import com.openforge.aimate.llm.StreamCallbacks;
import com.openforge.aimate.llm.model.ChatRequest;
import com.openforge.aimate.llm.model.ChatResponse;
import com.openforge.aimate.llm.model.FunctionCallResult;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.llm.model.Tool;
import com.openforge.aimate.llm.model.ToolCall;
import com.openforge.aimate.llm.model.ToolFunction;
import com.openforge.aimate.memory.EmbeddingClient;
import com.openforge.aimate.memory.EmbeddingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyService {

    private final LlmRouter llmRouter;
    private final UserApiKeyResolver keyResolver;
    private final UserEmbeddingResolver embeddingResolver;
    private final EmbeddingClient systemEmbeddingClient;
    private final EmbeddingProperties embeddingProperties;
    private final LlmCallLogService callLogService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService agentVirtualThreadExecutor;

    public SseEmitter streamChat(Long userId, ProxyChatRequest req) {
        SseEmitter emitter = new SseEmitter(300_000L);
        agentVirtualThreadExecutor.execute(() -> {
            long start = System.currentTimeMillis();
            String provider = "system_router";
            String model = req.model() != null ? req.model() : "unknown";
            try {
                ChatRequest chatRequest = toChatRequest(req);
                ChatResponse response;

                var userCfg = keyResolver.resolveDefaultLlm(userId);
                if (userCfg.isPresent()) {
                    LlmProperties.ProviderConfig cfg = userCfg.get();
                    provider = cfg.name();
                    model = cfg.model();
                    LlmClient client = new LlmClient(httpClient, objectMapper, cfg);
                    ChatRequest withModel = ChatRequest.builder()
                            .model(cfg.model())
                            .messages(chatRequest.messages())
                            .tools(chatRequest.tools())
                            .toolChoice(chatRequest.toolChoice())
                            .temperature(chatRequest.temperature())
                            .maxTokens(chatRequest.maxTokens())
                            .build();
                    response = client.streamChat(withModel, StreamCallbacks.contentOnly(token -> {
                        try {
                            emitter.send(SseEmitter.event().data(
                                    objectMapper.writeValueAsString(Map.of("type", "token", "token", token))));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }));
                } else {
                    response = llmRouter.streamChat(chatRequest, StreamCallbacks.contentOnly(token -> {
                        try {
                            emitter.send(SseEmitter.event().data(
                                    objectMapper.writeValueAsString(Map.of("type", "token", "token", token))));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }));
                    if (response.model() != null) model = response.model();
                }

                Message msg = response.firstMessage();
                String content = msg.content() != null ? msg.content() : "";
                List<Map<String, Object>> toolCalls = new ArrayList<>();
                if (msg.toolCalls() != null) {
                    for (ToolCall tc : msg.toolCalls()) {
                        toolCalls.add(Map.of(
                                "id", tc.id() != null ? tc.id() : "",
                                "type", tc.type() != null ? tc.type() : "function",
                                "function", Map.of(
                                        "name", tc.function() != null ? tc.function().name() : "",
                                        "arguments", tc.function() != null && tc.function().arguments() != null
                                                ? tc.function().arguments() : "{}"
                                )
                        ));
                    }
                }

                Map<String, Object> finalPayload = new java.util.LinkedHashMap<>();
                finalPayload.put("type", "final");
                finalPayload.put("content", content);
                finalPayload.put("model", model);
                if (!toolCalls.isEmpty()) finalPayload.put("toolCalls", toolCalls);

                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(finalPayload)));
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();

                callLogService.logSuccess(
                        provider, model, LlmCallLog.CallType.DESKTOP_AGENT,
                        null, "/api/proxy/chat/completions",
                        userId, req.sessionId(),
                        System.currentTimeMillis() - start, response);
            } catch (Exception e) {
                log.error("[Proxy] streamChat failed: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().data(
                            objectMapper.writeValueAsString(Map.of("type", "error", "error", e.getMessage() != null ? e.getMessage() : "error"))));
                } catch (Exception ignored) {
                    /* ignore */
                }
                callLogService.logError(
                        provider, model, LlmCallLog.CallType.DESKTOP_AGENT,
                        null, "/api/proxy/chat/completions",
                        userId, req.sessionId(),
                        System.currentTimeMillis() - start,
                        500, "PROXY_ERROR", e.getMessage());
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    public Map<String, Object> embed(Long userId, ProxyEmbedRequest req) {
        long start = System.currentTimeMillis();
        List<String> texts = req.texts();
        List<List<Float>> embeddings = new ArrayList<>();
        String model = embeddingProperties.model();
        String provider = "system_embedding";
        try {
            EmbeddingClient client = systemEmbeddingClient;
            var resolved = embeddingResolver.resolveDefault(userId);
            if (resolved.isPresent()) {
                provider = "user_embedding";
                model = resolved.get().props().model();
                client = new EmbeddingClient(httpClient, objectMapper, resolved.get().props());
            }
            for (String text : texts) {
                embeddings.add(client.embed(text));
            }
            callLogService.logSuccess(
                    provider, model, LlmCallLog.CallType.DESKTOP_AGENT,
                    "embeddings", "/api/proxy/embeddings",
                    userId, null,
                    System.currentTimeMillis() - start,
                    null);
            return Map.of(
                    "embeddings", embeddings,
                    "model", model,
                    "dimensions", embeddings.isEmpty() ? 0 : embeddings.get(0).size()
            );
        } catch (Exception e) {
            callLogService.logError(
                    provider, model, LlmCallLog.CallType.DESKTOP_AGENT,
                    "embeddings", "/api/proxy/embeddings",
                    userId, null,
                    System.currentTimeMillis() - start,
                    500, "EMBED_ERROR", e.getMessage());
            throw e;
        }
    }

    private ChatRequest toChatRequest(ProxyChatRequest req) {
        List<Message> messages = new ArrayList<>();
        if (req.messages() != null) {
            for (ProxyChatRequest.ProxyMessage m : req.messages()) {
                List<ToolCall> toolCalls = null;
                if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                    toolCalls = m.toolCalls().stream()
                            .map(tc -> new ToolCall(
                                    tc.id(),
                                    tc.type() != null ? tc.type() : "function",
                                    tc.function() != null
                                            ? new FunctionCallResult(tc.function().name(), tc.function().arguments())
                                            : null
                            ))
                            .toList();
                }
                messages.add(Message.builder()
                        .role(m.role())
                        .content(m.content())
                        .toolCalls(toolCalls)
                        .toolCallId(m.toolCallId())
                        .reasoningContent(m.reasoningContent() != null ? m.reasoningContent() : "")
                        .build());
            }
        }

        List<Tool> tools = null;
        if (req.tools() != null && !req.tools().isEmpty()) {
            tools = req.tools().stream()
                    .map(t -> new Tool(
                            t.type() != null ? t.type() : "function",
                            new ToolFunction(
                                    t.function().name(),
                                    t.function().description(),
                                    t.function().parameters()
                            )
                    ))
                    .toList();
        }

        return ChatRequest.builder()
                .model(req.model())
                .messages(messages)
                .tools(tools)
                .toolChoice(req.toolChoice())
                .temperature(req.temperature() != null ? req.temperature() : 0.7)
                .maxTokens(req.maxTokens() != null ? req.maxTokens() : 8192)
                .build();
    }
}
