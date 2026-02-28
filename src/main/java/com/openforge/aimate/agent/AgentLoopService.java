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
    private static final int MAX_ITERATIONS      = 30;
    private static final int MEMORY_RECALL_TOP_K = 5;

    private static final String BASE_SYSTEM_PROMPT =
            """
            You are an autonomous AI agent with access to a set of tools.
            Think step by step. Use tools ONLY when they are clearly helpful.

            Long-term memory:
            - Use the "store_memory" tool ONLY for information that will be useful across many future tasks
              (for example: stable user preferences, long-term goals, important facts about the user or system).
            - Do NOT call "store_memory" repeatedly with the same or very similar content.
            - Do NOT store trivial restatements of the current question.

            Answering:
            - When you have enough information to answer the user's task completely, provide a final answer
              WITHOUT calling any more tools.
            - Prefer fewer, higher-quality tool calls over many repetitive ones.

            Be concise but thorough. Think out loud as you reason.
            """;

    private final LlmRouter              llmRouter;
    private final AgentContextService    contextService;
    private final AgentEventPublisher    publisher;
    private final AgentSessionRepository sessionRepository;
    private final AgentToolRepository    toolRepository;
    private final LongTermMemoryService  memoryService;
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

    private void executeLoop(AgentSession session) {
        String sessionId = session.getSessionId();
        log.info("[Agent:{}] Loop started. Task: {}", sessionId, session.getTaskDescription());

        try {
            session.setStatus(AgentSession.SessionStatus.RUNNING);
            sessionRepository.save(session);
            publisher.publish(AgentEvent.statusChange(sessionId, "RUNNING"));

            // ── Resolve LLM caller (user key > system fallback) ──────────────
            var llmCaller = buildCaller(session);
            final Long userId = session.getUserId();

            // ── RECALL: search long-term memory before first iteration ───────
            // Only initialize context when this is the very first run
            // (context window is empty). For subsequent runs (multi-turn),
            // we keep the existing context and simply append new user messages.
            List<Message> existingContext = contextService.load(session);
            if (existingContext.isEmpty()) {
                String systemPrompt = buildSystemPromptWithMemory(
                        session.getTaskDescription(), userId, sessionId);
                contextService.initialize(session, List.of(
                        Message.system(systemPrompt),
                        Message.user(session.getTaskDescription())
                ));
            } else {
                log.debug("[Agent:{}] Resuming with existing context ({} messages).",
                        sessionId, existingContext.size());
            }

            // ── The loop ─────────────────────────────────────────────────────
            while (true) {
                session = sessionRepository.findBySessionId(sessionId)
                        .orElseThrow(() -> new IllegalStateException("Session vanished: " + sessionId));

                if (session.getStatus() == AgentSession.SessionStatus.PAUSED) {
                    log.info("[Agent:{}] Paused. Waiting for resume signal...", sessionId);
                    waitForResume(session);
                    continue;
                }
                if (session.getStatus() != AgentSession.SessionStatus.RUNNING) {
                    log.info("[Agent:{}] Status={}, exiting loop.", sessionId, session.getStatus());
                    break;
                }

                int iteration = session.getIterationCount() + 1;
                publisher.publish(AgentEvent.iterationStart(sessionId, iteration));
                log.debug("[Agent:{}] Iteration {}", sessionId, iteration);

                // ── THINK (streaming) ────────────────────────────────────────
                List<Message> context = contextService.load(session);
                List<Tool>    tools   = loadActiveTools();

                ChatRequest request = ChatRequest.withTools(
                        "",
                        context,
                        tools.isEmpty() ? null : tools
                );

                final AgentSession finalSession = session;
                final int          finalIter    = iteration;

                ChatResponse response = llmCaller.apply(request,
                        token -> publisher.publish(
                                AgentEvent.thinking(sessionId, token, finalIter)));

                // ── DECIDE ───────────────────────────────────────────────────
                Message assistantMessage = response.firstMessage();

                if (response.hasToolCalls()) {
                    // ── ACT ──────────────────────────────────────────────────
                    contextService.append(finalSession, assistantMessage);

                    for (ToolCall toolCall : assistantMessage.toolCalls()) {
                        publisher.publish(AgentEvent.toolCall(sessionId, toolCall, finalIter));

                        String toolResult = executeTool(toolCall, sessionId);
                        publisher.publish(AgentEvent.toolResult(
                                sessionId, toolCall.function().name(), toolResult, finalIter));

                        contextService.append(finalSession,
                                Message.toolResult(toolCall.id(), toolResult));

                        // Store significant tool results as episodic memories
                        maybeRememberToolResult(sessionId, toolCall.function().name(), toolResult, userId);
                    }

                } else {
                    // ── DONE — final answer ──────────────────────────────────
                    String answer = assistantMessage.content();

                    // Reload latest session row to avoid optimistic-lock conflict
                    // with concurrent context updates (contextService.append).
                    AgentSession current = sessionRepository.findById(session.getId())
                            .orElseThrow(() -> new IllegalStateException("Session vanished: " + sessionId));
                    current.setResult(answer);
                    current.setStatus(AgentSession.SessionStatus.COMPLETED);
                    current.setIterationCount(iteration);
                    sessionRepository.save(current);
                    session = current;

                    publisher.publish(AgentEvent.finalAnswer(sessionId, answer, iteration));
                    publisher.publish(AgentEvent.statusChange(sessionId, "COMPLETED"));
                    log.info("[Agent:{}] Completed in {} iteration(s).", sessionId, iteration);

                    // Store the completed task+answer as a semantic memory
                    storeCompletionMemory(sessionId, session.getTaskDescription(), answer, userId);
                    break;
                }

                // ── Persist iteration progress safely (avoid stale version) ──
                AgentSession current = sessionRepository.findById(session.getId())
                        .orElseThrow(() -> new IllegalStateException("Session vanished: " + sessionId));
                current.setIterationCount(iteration);
                sessionRepository.save(current);
                session = current;

                if (iteration >= MAX_ITERATIONS) {
                    log.warn("[Agent:{}] Max iterations ({}) reached.", sessionId, MAX_ITERATIONS);
                    failSession(session, "Max iterations (%d) reached without final answer."
                            .formatted(MAX_ITERATIONS));
                    publisher.publish(AgentEvent.error(sessionId, "Max iterations reached", iteration));
                    break;
                }
            }

        } catch (Exception e) {
            log.error("[Agent:{}] Unhandled exception in loop: {}", sessionId, e.getMessage(), e);
            try {
                session = sessionRepository.findBySessionId(sessionId).orElse(session);
                failSession(session, e.getMessage());
            } catch (Exception ignored) {}
            publisher.publish(AgentEvent.error(sessionId, e.getMessage(), session.getIterationCount()));
        }
    }

    // ── Memory integration ───────────────────────────────────────────────────

    /**
     * Build the system prompt enriched with relevant long-term memories.
     *
     * We inject two tiers of memory (when available):
     *   1. User-level semantic profile — stable facts about the user (cross-session)
     *   2. Session-level episodic memories — events & tool results from this session
     */
    private String buildSystemPromptWithMemory(String taskDescription, Long userId, String sessionId) {
        try {
            List<MemoryRecord> userMemories =
                    userId != null
                            ? memoryService.recallUserSemantic(taskDescription, MEMORY_RECALL_TOP_K, userId)
                            : List.of();
            List<MemoryRecord> sessionMemories =
                    memoryService.recallFromSession(taskDescription, sessionId, MEMORY_RECALL_TOP_K);

            StringBuilder sb = new StringBuilder(BASE_SYSTEM_PROMPT);

            if (!userMemories.isEmpty()) {
                sb.append("\n\n## User profile (long-term)\n");
                for (MemoryRecord m : userMemories) {
                    sb.append("- [")
                      .append(m.memoryType().name())
                      .append("] ")
                      .append(m.content())
                      .append(" (importance: ")
                      .append(String.format("%.2f", m.importance()))
                      .append(")\n");
                }
                log.debug("[Agent] Injecting {} user-level memories into system prompt", userMemories.size());
            }

            if (!sessionMemories.isEmpty()) {
                String sessionBlock = memoryService.formatForPrompt(sessionMemories);
                sb.append("\n\n").append(sessionBlock);
                log.debug("[Agent] Injecting {} session-level memories into system prompt",
                        sessionMemories.size());
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("[Agent] Memory recall failed, proceeding without memories: {}", e.getMessage());
            return BASE_SYSTEM_PROMPT;
        }
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

    private String executeTool(ToolCall toolCall, String sessionId) {
        String toolName  = toolCall.function().name();
        String arguments = toolCall.function().arguments();
        log.info("[Agent:{}] Executing tool: {} args={}", sessionId, toolName, arguments);

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
     * Built-in memory tool: the Agent can explicitly decide to remember something.
     *
     * Expected arguments JSON:
     * { "content": "...", "memory_type": "SEMANTIC", "importance": 0.9 }
     */
    private String executeStoreMemoryTool(String sessionId, String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String    content    = args.path("content").asText();
            String    typeStr    = args.path("memory_type").asText("SEMANTIC");
            float     importance = (float) args.path("importance").asDouble(0.8);

            if (content == null || content.isBlank()) {
                return "[ToolError] store_memory called with empty content — skipping.";
            }

            // Deduplicate: if we've already stored exactly this content for this session
            // in the current JVM, skip writing it again. This helps models that tend to
            // over-use the memory tool with identical facts.
            Set<String> seen =
                    sessionStoredMemories.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());
            if (!seen.add(content)) {
                return "Memory already stored previously; skipping duplicate.";
            }

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

    // ── Tool loading ─────────────────────────────────────────────────────────

    /**
     * Load all active tools PLUS the built-in store_memory tool.
     * Called on every iteration so right-brain-created tools are picked up live.
     */
    private List<Tool> loadActiveTools() {
        List<Tool> tools = new ArrayList<>();

        // Built-in: store_memory — always available
        tools.add(buildStoreMemoryTool());

        // Dynamic tools from DB
        toolRepository.findByIsActiveTrue().stream()
                .map(this::toTool)
                .forEach(tools::add);

        return tools;
    }

    private Tool buildStoreMemoryTool() {
        String schema = """
                {
                  "type":"object",
                  "properties":{
                    "content":{
                      "type":"string",
                      "description":"A stable, long-term fact that will be useful in many future tasks (e.g. persistent user preferences, long-term goals). Do NOT store the current question verbatim or trivial restatements."
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
