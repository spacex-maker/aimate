package com.openforge.aimate.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.agent.event.AgentEvent;
import com.openforge.aimate.apikey.UserApiKeyResolver;
import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.domain.AgentTool;
import com.openforge.aimate.llm.LlmClient;
import com.openforge.aimate.llm.LlmProperties;
import com.openforge.aimate.llm.LlmRouter;
import com.openforge.aimate.llm.model.ChatRequest;
import com.openforge.aimate.llm.model.ChatResponse;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.llm.model.Tool;
import com.openforge.aimate.llm.model.ToolCall;
import com.openforge.aimate.llm.model.ToolFunction;
import com.openforge.aimate.memory.LongTermMemoryService;
import com.openforge.aimate.memory.MemoryRecord;
import com.openforge.aimate.memory.MemoryType;
import com.openforge.aimate.repository.AgentSessionRepository;
import com.openforge.aimate.repository.AgentToolRepository;
import com.openforge.aimate.websocket.AgentEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Agent's autonomous thinking loop.
 *
 * Loop shape (with long-term memory):
 *   while RUNNING:
 *     1. RECALL    — search Milvus for memories relevant to current task
 *     2. PERCEIVE  — inject recalled memories into system prompt
 *     3. THINK     — stream LLM response, pushing each token to WebSocket
 *     4. DECIDE    — tool call? → ACT and loop; final answer? → REMEMBER + DONE
 *     5. ACT       — execute tool(s), append results to context
 *     6. PERSIST   — save context + iteration count to DB
 *     7. CHECK     — guard against infinite loops (MAX_ITERATIONS)
 *
 * Memory integration:
 *   - On session init: recall top-5 relevant memories and inject into system prompt
 *   - On tool result:  store noteworthy results as EPISODIC memories (async-safe)
 *   - On final answer: store the answer as a SEMANTIC memory with high importance
 *   - The Agent can also call the built-in "store_memory" tool to explicitly remember
 *
 * JDK 25 — ScopedValue:
 *   SESSION_SCOPE carries the sessionId through the entire call stack.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentLoopService {

    // ── JDK 25: ScopedValue ──────────────────────────────────────────────────
    public static final ScopedValue<String> SESSION_SCOPE = ScopedValue.newInstance();

    // ── Safety limits ────────────────────────────────────────────────────────
    private static final int MAX_ITERATIONS           = 30;
    /** Max number of tools to pass to the LLM (retrieved by semantic relevance). */
    private static final int TOP_K_TOOLS              = 12;
    /** Prefix length for "same topic" deduplication (e.g. "我的名字是forge，用户希望" catches both variants). */
    private static final int STORE_MEMORY_PREFIX_LEN = 15;

    private static final String BASE_SYSTEM_PROMPT =
            """
            You are an autonomous AI agent with access to tools. Think step by step and decide for yourself when to use which tool.
            
            Tools (use only when you judge it helps):
            - recall_memory: search long-term memory by query (e.g. user name, preferences, past facts). Use when the user's question might be answered from something you stored before.
            - store_memory: save a fact for future sessions. Use at most once per distinct, important fact; then reply in natural language.
            
            Memory writing rules (VERY IMPORTANT):
            - When calling store_memory, ALWAYS rewrite the fact into a clear, third-person sentence with an explicit subject.
              For example: "用户的名字是 Zed", "智能体的名字是 Forge", "用户是 Java 后端开发人员".
            - NEVER store ambiguous first-person sentences like "我的名字是 Forge", "I am a Java developer", "我是 Java 开发".
              Before storing, rewrite them so that it is clear whether the fact is about the USER or about the ASSISTANT.
            - If a fact is about the user, use "用户…" / "the user…". If it is about you (the assistant), use "智能体…" / "the assistant…".
            
            When you can answer directly, reply in natural language without calling tools. Be concise. Think out loud as you reason.
            """;

    private final LlmRouter              llmRouter;
    private final AgentContextService    contextService;
    private final AgentEventPublisher    publisher;
    private final AgentSessionRepository sessionRepository;
    private final AgentToolRepository    toolRepository;
    private final LongTermMemoryService  memoryService;
    private final ToolIndexService       toolIndexService;
    private final ObjectMapper           objectMapper;
    private final UserApiKeyResolver     keyResolver;
    private final HttpClient             httpClient;

    // Tracks which exact contents have already been stored via store_memory
    // in this JVM, per sessionId, to avoid spamming duplicate memories.
    private final Map<String, Set<String>> sessionStoredMemories =
            new ConcurrentHashMap<>();

    // ── Entry point ──────────────────────────────────────────────────────────

    public void run(AgentSession session) {
        ScopedValue.where(SESSION_SCOPE, session.getSessionId())
                .run(() -> executeLoop(session));
    }

    // ── Main loop ────────────────────────────────────────────────────────────

    // ── Session-scoped LLM caller ────────────────────────────────────────────

    /**
     * Returns a lambda that calls the user's own LLM key if configured,
     * otherwise delegates to the system-level LlmRouter (fallback).
     */
    private java.util.function.BiFunction<ChatRequest, java.util.function.Consumer<String>, ChatResponse>
    buildCaller(AgentSession session) {
        return keyResolver.resolveDefaultLlm(session.getUserId())
                .map(config -> {
                    LlmClient userClient = new LlmClient(httpClient, objectMapper, config);
                    log.info("[Agent:{}] Using user key — provider={} model={}",
                            session.getSessionId(), config.name(), config.model());
                    return (java.util.function.BiFunction<ChatRequest, java.util.function.Consumer<String>, ChatResponse>)
                            (req, cb) -> userClient.streamChat(req, cb);
                })
                .orElseGet(() -> {
                    log.info("[Agent:{}] No user key found — using system LlmRouter", session.getSessionId());
                    return (req, cb) -> llmRouter.streamChat(req, cb);
                });
    }

    private static final List<String> EXECUTION_PLAN = List.of("回忆", "思考与执行", "回答");

    private void executeLoop(AgentSession session) {
        String sessionId = session.getSessionId();
        log.info("[Agent:{}] Loop started. Task: {}", sessionId, session.getTaskDescription());

        try {
            session.setStatus(AgentSession.SessionStatus.RUNNING);
            sessionRepository.save(session);
            publisher.publish(AgentEvent.statusChange(sessionId, "RUNNING"));

            var llmCaller = buildCaller(session);
            final Long userId = session.getUserId();

            // ── 1. Output plan to frontend ────────────────────────────────────
            publisher.publish(AgentEvent.planReady(sessionId, new ArrayList<>(EXECUTION_PLAN)));

            // ── Step 1: 回忆 ──────────────────────────────────────────────────
            publisher.publish(AgentEvent.stepStart(sessionId, 1, EXECUTION_PLAN.get(0)));
            List<Message> existingContext = contextService.load(session);
            if (existingContext.isEmpty()) {
                String systemPrompt = buildSystemPrompt();
                contextService.initialize(session, List.of(
                        Message.system(systemPrompt),
                        Message.user(session.getTaskDescription())
                ));
            } else {
                log.debug("[Agent:{}] Resuming with existing context ({} messages).",
                        sessionId, existingContext.size());
            }
            publisher.publish(AgentEvent.stepComplete(sessionId, 1, EXECUTION_PLAN.get(0), "已回忆并注入上下文"));

            // ── Step 2: 思考与执行 ───────────────────────────────────────────
            publisher.publish(AgentEvent.stepStart(sessionId, 2, EXECUTION_PLAN.get(1)));
            String finalAnswer = runThinkAndActLoop(session, sessionId, llmCaller, userId);
            int lastIteration = sessionRepository.findBySessionId(sessionId)
                    .map(AgentSession::getIterationCount).orElse(0);
            publisher.publish(AgentEvent.stepComplete(sessionId, 2, EXECUTION_PLAN.get(1),
                    finalAnswer != null ? "完成推理" : "达到最大迭代次数"));

            // ── Step 3: 回答 ──────────────────────────────────────────────────
            publisher.publish(AgentEvent.stepStart(sessionId, 3, EXECUTION_PLAN.get(2)));
            session = sessionRepository.findBySessionId(sessionId)
                    .orElseThrow(() -> new IllegalStateException("Session vanished: " + sessionId));

            if (finalAnswer != null) {
                session.setResult(finalAnswer);
                session.setStatus(AgentSession.SessionStatus.COMPLETED);
                sessionRepository.save(session);
                publisher.publish(AgentEvent.stepComplete(sessionId, 3, EXECUTION_PLAN.get(2), finalAnswer));
                publisher.publish(AgentEvent.finalAnswer(sessionId, finalAnswer, lastIteration));
                publisher.publish(AgentEvent.statusChange(sessionId, "COMPLETED"));
                storeCompletionMemory(sessionId, session.getTaskDescription(), finalAnswer, userId);
                log.info("[Agent:{}] Completed. Plan executed.", sessionId);
            } else {
                failSession(session, "Max iterations (%d) reached without final answer.".formatted(MAX_ITERATIONS));
                publisher.publish(AgentEvent.stepComplete(sessionId, 3, EXECUTION_PLAN.get(2), "未得到最终回答"));
                publisher.publish(AgentEvent.error(sessionId, "Max iterations reached", lastIteration));
                publisher.publish(AgentEvent.statusChange(sessionId, "FAILED"));
            }

        } catch (Exception e) {
            log.error("[Agent:{}] Unhandled exception: {}", sessionId, e.getMessage(), e);
            try {
                session = sessionRepository.findBySessionId(sessionId).orElse(session);
                failSession(session, e.getMessage());
            } catch (Exception ignored) {}
            publisher.publish(AgentEvent.error(sessionId, e.getMessage(), session.getIterationCount()));
            publisher.publish(AgentEvent.statusChange(sessionId, "FAILED"));
        }
    }

    /**
     * Inner loop: think (stream) → tool calls or final answer.
     * Returns the final answer text, or null if max iterations reached.
     */
    private String runThinkAndActLoop(AgentSession session, String sessionId,
                                      java.util.function.BiFunction<ChatRequest, java.util.function.Consumer<String>, ChatResponse> llmCaller,
                                      Long userId) {
        int iteration = 0;
        while (true) {
            session = sessionRepository.findBySessionId(sessionId)
                    .orElseThrow(() -> new IllegalStateException("Session vanished: " + sessionId));

            if (session.getStatus() == AgentSession.SessionStatus.PAUSED) {
                log.info("[Agent:{}] Paused. Waiting for resume...", sessionId);
                waitForResume(session);
                continue;
            }
            if (session.getStatus() != AgentSession.SessionStatus.RUNNING) {
                return null;
            }

            iteration = session.getIterationCount() + 1;
            publisher.publish(AgentEvent.iterationStart(sessionId, iteration));

            List<Message> context = contextService.load(session);
            String        queryForTools = lastUserMessageFrom(context, session.getTaskDescription());
            List<Tool>    tools   = loadRelevantTools(queryForTools, TOP_K_TOOLS, userId);
            ChatRequest   request = ChatRequest.withTools("", context, tools.isEmpty() ? null : tools);
            final AgentSession finalSession = session;
            final int          finalIter   = iteration;

            ChatResponse response = llmCaller.apply(request,
                    token -> publisher.publish(AgentEvent.thinking(sessionId, token, finalIter)));

            Message assistantMessage = response.firstMessage();

            if (response.hasToolCalls()) {
                // Append assistant + all tool results in one go. Otherwise the second append()
                // would load stale session.getContextWindow() (never refreshed after first persist)
                // and overwrite DB without the assistant message — so the model never sees
                // that it already called a tool and loops forever.
                List<Message> toAppend = new ArrayList<>();
                toAppend.add(assistantMessage);
                for (ToolCall toolCall : assistantMessage.toolCalls()) {
                    publisher.publish(AgentEvent.toolCall(sessionId, toolCall, finalIter));
                    String toolResult = executeTool(toolCall, sessionId, userId);
                    publisher.publish(AgentEvent.toolResult(
                            sessionId, toolCall.function().name(), toolResult, finalIter));
                    toAppend.add(Message.toolResult(toolCall.id(), toolResult));
                    maybeRememberToolResult(sessionId, toolCall.function().name(), toolResult, userId);
                }
                contextService.append(finalSession, toAppend.toArray(new Message[0]));
            } else {
                String answer = assistantMessage.content();
                AgentSession current = sessionRepository.findById(session.getId())
                        .orElseThrow(() -> new IllegalStateException("Session vanished: " + sessionId));
                current.setIterationCount(iteration);
                sessionRepository.save(current);
                return answer;
            }

            AgentSession current = sessionRepository.findById(session.getId())
                    .orElseThrow(() -> new IllegalStateException("Session vanished: " + sessionId));
            current.setIterationCount(iteration);
            sessionRepository.save(current);
            session = current;

            if (iteration >= MAX_ITERATIONS) {
                log.warn("[Agent:{}] Max iterations ({}) reached.", sessionId, MAX_ITERATIONS);
                return null;
            }
        }
    }

    // ── Memory integration ───────────────────────────────────────────────────

    /**
     * Build the system prompt. Memory is not pre-injected; the agent uses
     * recall_memory when it needs past/user information (intent → recall → answer).
     */
    private String buildSystemPrompt() {
        return BASE_SYSTEM_PROMPT;
    }

    /**
     * Store a completed task+answer as a SEMANTIC memory.
     * This is the primary mechanism by which the Agent "learns" across sessions.
     */
    private void storeCompletionMemory(String sessionId, String task, String answer, Long userId) {
        String memoryContent = "Task: %s\nAnswer: %s".formatted(
                task.substring(0, Math.min(200, task.length())),
                answer.substring(0, Math.min(500, answer.length())));

        memoryService.remember(sessionId, memoryContent, MemoryType.SEMANTIC, 0.85f, userId);
        log.debug("[Agent:{}] Stored completion memory.", sessionId);
    }

    /**
     * Store a tool result as an EPISODIC memory if it looks non-trivial.
     * Avoids storing stub results or short error messages.
     */
    private void maybeRememberToolResult(String sessionId, String toolName, String result, Long userId) {
        if (result == null || result.startsWith("[STUB]") || result.startsWith("[ToolError]")) return;

        // 内置的 store_memory 已经在 executeStoreMemoryTool 中把真正重要的内容
        // 以 SEMANTIC/PROCEDURAL 方式写入长期记忆，这里如果再按「工具结果」
        // 统一存 EPISODIC 会造成同一条画像被重复记录多次，看起来像在无限记忆。
        // 因此对 store_memory 直接跳过，不再额外写 EPISODIC。
        if ("store_memory".equals(toolName)) return;
        if (result.length() < 50) return;  // too short to be meaningful

        String content = "Tool '%s' returned: %s".formatted(
                toolName, result.substring(0, Math.min(300, result.length())));
        memoryService.remember(sessionId, content, MemoryType.EPISODIC, 0.6f, userId);
    }

    // ── Tool execution ───────────────────────────────────────────────────────

    private String executeTool(ToolCall toolCall, String sessionId, Long userId) {
        String toolName  = toolCall.function().name();
        String arguments = toolCall.function().arguments();
        log.info("[Agent:{}] Executing tool: {} args={}", sessionId, toolName, arguments);

        // Built-in: recall_memory — retrieve relevant long-term memories (intent → recall → then answer)
        if ("recall_memory".equals(toolName)) {
            return executeRecallMemoryTool(sessionId, arguments, userId);
        }
        // Built-in: store_memory tool (lets Agent explicitly create long-term memories)
        if ("store_memory".equals(toolName)) {
            return executeStoreMemoryTool(sessionId, arguments);
        }

        AgentTool tool = toolRepository.findByToolName(toolName).orElse(null);
        if (tool == null) return "[ToolError] Unknown tool: " + toolName;

        return switch (tool.getToolType()) {
            case JAVA_NATIVE  -> executeJavaTool(tool, arguments, sessionId);
            case PYTHON_SCRIPT, NODE_SCRIPT, SHELL_CMD ->
                    executeScriptTool(tool, arguments, sessionId);
        };
    }

    /**
     * Built-in recall_memory tool: retrieve relevant long-term memories by semantic search.
     * Flow: AI does intent analysis → calls recall_memory with a query → uses returned memories to answer.
     */
    private String executeRecallMemoryTool(String sessionId, String argumentsJson, Long userId) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String query = args.path("query").asText("");
            int topK = args.path("top_k").asInt(10);
            if (query == null || query.isBlank()) {
                return "[ToolError] recall_memory requires a non-empty 'query' (e.g. user's name, user preferences, past topic).";
            }
            topK = Math.min(20, Math.max(1, topK));
            List<MemoryRecord> memories = memoryService.recall(query, topK, userId);
            // 如果主召回路径没有命中（例如索引或阈值问题），退回到与前端相同的 searchMemories 逻辑，
            // 确保压缩后的记忆、历史记忆都能被 AI 找到。
            if (memories.isEmpty()) {
                List<com.openforge.aimate.memory.MemoryItem> items =
                        memoryService.searchMemories(query, topK, userId);
                memories = new java.util.ArrayList<>();
                for (com.openforge.aimate.memory.MemoryItem item : items) {
                    memories.add(new MemoryRecord(
                            item.content(),
                            item.memoryType(),
                            item.sessionId(),
                            item.importance(),
                            item.score() != null ? item.score() : 0.0
                    ));
                }
            }
            if (memories.isEmpty()) {
                return "No relevant memories found for this query. You can answer from general knowledge or say you don't recall.";
            }
            String block = memoryService.formatForPrompt(memories);
            log.debug("[Agent:{}] recall_memory returned {} memories for query", sessionId, memories.size());
            return block != null ? block : "No relevant memories found.";
        } catch (Exception e) {
            log.warn("[Agent:{}] recall_memory failed: {}", sessionId, e.getMessage());
            return "[ToolError] recall_memory failed: " + e.getMessage();
        }
    }

    /**
     * Built-in memory tool: the Agent can explicitly decide to remember something.
     * Deduplication: normalized exact match + same-topic prefix to stop repeated store_memory loops.
     */
    private String executeStoreMemoryTool(String sessionId, String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String   content    = args.path("content").asText();
            String   typeStr    = args.path("memory_type").asText("SEMANTIC");
            float    importance = (float) args.path("importance").asDouble(0.8);

            if (content == null || content.isBlank()) {
                return "[ToolError] store_memory called with empty content — skipping.";
            }

            String     normalized = normalizeForDedupe(content);
            Set<String> seen      = sessionStoredMemories.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());

            if (seen.contains(normalized)) {
                return "Memory already stored previously; skipping duplicate. Reply to the user now.";
            }
            String prefix = prefixOf(normalized, STORE_MEMORY_PREFIX_LEN);
            for (String existing : seen) {
                if (prefix.equals(prefixOf(existing, STORE_MEMORY_PREFIX_LEN))) {
                    log.debug("[Agent:{}] store_memory skipped (same-topic prefix): {}", sessionId, prefix);
                    return "Already stored similar content. Reply to the user in natural language now.";
                }
            }
            seen.add(normalized);

            MemoryType memType;
            try { memType = MemoryType.valueOf(typeStr.toUpperCase()); }
            catch (Exception e) { memType = MemoryType.SEMANTIC; }

            Long currentUserId = null;
            try {
                currentUserId = sessionRepository.findBySessionId(sessionId)
                        .map(com.openforge.aimate.domain.AgentSession::getUserId).orElse(null);
            } catch (Exception ignored) {}
            memoryService.remember(sessionId, content, memType, importance, currentUserId);
            return "Memory stored successfully.";
        } catch (Exception e) {
            return "[ToolError] store_memory failed: " + e.getMessage();
        }
    }

    private static String normalizeForDedupe(String content) {
        if (content == null) return "";
        return content.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private static String prefixOf(String s, int maxLen) {
        if (s == null || s.isEmpty()) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private String executeJavaTool(AgentTool tool, String arguments, String sessionId) {
        log.debug("[Agent:{}] Java tool '{}' entryPoint={}",
                sessionId, tool.getToolName(), tool.getEntryPoint());
        // TODO (Phase 3): ApplicationContext.getBean(tool.getEntryPoint()).execute(args)
        return "[STUB] Java tool '%s' would execute here. args=%s"
                .formatted(tool.getToolName(), arguments);
    }

    private String executeScriptTool(AgentTool tool, String arguments, String sessionId) {
        log.debug("[Agent:{}] Script tool '{}' type={}",
                sessionId, tool.getToolName(), tool.getToolType());
        // TODO (Phase 3): ProcessBuilder sandbox execution
        return "[STUB] Script tool '%s' (%s) would execute here. args=%s"
                .formatted(tool.getToolName(), tool.getToolType(), arguments);
    }

    // ── Tool loading (vectorized: only pass semantically relevant tools) ─────

    /**
     * Returns the last user message content from context, or taskDescription if none.
     * Used as the query for tool retrieval.
     */
    private String lastUserMessageFrom(List<Message> context, String taskDescription) {
        if (context == null || context.isEmpty()) return taskDescription != null ? taskDescription : "";
        for (int i = context.size() - 1; i >= 0; i--) {
            Message m = context.get(i);
            if ("user".equals(m.role()) && m.content() != null && !m.content().isBlank()) {
                return m.content();
            }
        }
        return taskDescription != null ? taskDescription : "";
    }

    /**
     * Load tools by semantic relevance to the current query (e.g. last user message).
     * Uses the current user's embedding model for tool search (same as memories).
     * When the index is unavailable or returns nothing, falls back to all active tools.
     */
    private List<Tool> loadRelevantTools(String queryText, int topK, Long userId) {
        List<String> relevantIds = toolIndexService.searchRelevantTools(queryText, topK, userId);
        if (relevantIds == null || relevantIds.isEmpty()) {
            return loadAllTools();
        }
        List<Tool> tools = new ArrayList<>();
        Set<String> added = new java.util.LinkedHashSet<>();
        for (String id : relevantIds) {
            if (id == null || !added.add(id)) continue;
            if ("recall_memory".equals(id)) {
                tools.add(buildRecallMemoryTool());
            } else if ("store_memory".equals(id)) {
                tools.add(buildStoreMemoryTool());
            } else {
                toolRepository.findByToolName(id).map(this::toTool).ifPresent(tools::add);
            }
        }
        return tools;
    }

    /** Fallback: all built-in + all active DB tools (used when tool index is empty or disabled). */
    private List<Tool> loadAllTools() {
        List<Tool> tools = new ArrayList<>();
        tools.add(buildRecallMemoryTool());
        tools.add(buildStoreMemoryTool());
        toolRepository.findByIsActiveTrue().stream()
                .map(this::toTool)
                .forEach(tools::add);
        return tools;
    }

    private Tool buildRecallMemoryTool() {
        String schema = """
                {
                  "type":"object",
                  "properties":{
                    "query":{
                      "type":"string",
                      "description":"Natural language query for what to recall (e.g. '用户的名字', 'user name', '用户偏好', 'what the user told me before'). Use the user's question or intent in Chinese or English."
                    },
                    "top_k":{
                      "type":"integer",
                      "minimum":1,
                      "maximum":20,
                      "description":"Max number of memories to return (default 10)."
                    }
                  },
                  "required":["query"]
                }
                """;
        try {
            JsonNode params = objectMapper.readTree(schema);
            return Tool.ofFunction(new ToolFunction(
                    "recall_memory",
                    "Search long-term memory by natural language query. Returns relevant past information (e.g. user profile, name, preferences). Use when you need to look up something that may have been stored before.",
                    params
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build recall_memory tool schema", e);
        }
    }

    private Tool buildStoreMemoryTool() {
        String schema = """
                {
                  "type":"object",
                  "properties":{
                    "content":{
                      "type":"string",
                      "description":"A stable, long-term fact that will be useful in many future tasks (e.g. persistent user preferences, long-term goals). ALWAYS rewrite the fact into a clear third-person sentence with an explicit subject before storing it. For example: '用户的名字是 Zed', '智能体的名字是 Forge', '用户是 Java 后端开发人员'. NEVER store ambiguous first-person sentences like '我的名字是 Forge' or 'I am a Java developer' — rewrite them to refer explicitly to the user or the assistant first, then call this tool."
                    },
                    "memory_type":{
                      "type":"string",
                      "enum":["EPISODIC","SEMANTIC","PROCEDURAL"]
                    },
                    "importance":{
                      "type":"number",
                      "minimum":0,
                      "maximum":1
                    }
                  },
                  "required":["content"]
                }
                """;
        try {
            JsonNode params = objectMapper.readTree(schema);
            return Tool.ofFunction(new ToolFunction(
                    "store_memory",
                    "Store an IMPORTANT, long-term piece of information into memory for future sessions. Use sparingly. Only call this for facts that will matter across many tasks, not for one-off details.",
                    params
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build store_memory tool schema", e);
        }
    }

    private Tool toTool(AgentTool agentTool) {
        JsonNode parameters;
        try {
            parameters = objectMapper.readTree(agentTool.getInputSchema());
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse inputSchema for tool {}: {}", agentTool.getToolName(), e.getMessage());
            parameters = objectMapper.createObjectNode();
        }
        return Tool.ofFunction(new ToolFunction(
                agentTool.getToolName(),
                agentTool.getToolDescription(),
                parameters
        ));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void failSession(AgentSession session, String reason) {
        session.setStatus(AgentSession.SessionStatus.FAILED);
        session.setErrorMessage(reason);
        sessionRepository.save(session);
        publisher.publish(AgentEvent.statusChange(session.getSessionId(), "FAILED"));
    }

    private void waitForResume(AgentSession session) {
        while (true) {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            AgentSession fresh = sessionRepository.findBySessionId(session.getSessionId())
                    .orElse(session);
            if (fresh.getStatus() != AgentSession.SessionStatus.PAUSED) return;
        }
    }
}
