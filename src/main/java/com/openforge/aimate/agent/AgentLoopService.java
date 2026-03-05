package com.openforge.aimate.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.agent.dto.SessionResponse;
import com.openforge.aimate.agent.event.AgentEvent;
import com.openforge.aimate.apikey.UserApiKeyResolver;
import com.openforge.aimate.domain.LlmCallLog;
import com.openforge.aimate.llm.LlmCallLogService;
import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.domain.AgentTool;
import com.openforge.aimate.domain.SessionMessage;
import com.openforge.aimate.llm.LlmClient;
import com.openforge.aimate.llm.LlmProperties;
import com.openforge.aimate.llm.LlmRouter;
import com.openforge.aimate.llm.StreamCallbacks;
import com.openforge.aimate.llm.model.ChatRequest;
import com.openforge.aimate.llm.model.ChatResponse;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.llm.model.Tool;
import com.openforge.aimate.llm.model.ToolCall;
import com.openforge.aimate.llm.model.ToolFunction;
import com.openforge.aimate.config.SystemConfigService;
import com.openforge.aimate.memory.LongTermMemoryService;
import com.openforge.aimate.memory.MemoryRecord;
import com.openforge.aimate.memory.MemoryType;
import com.openforge.aimate.domain.UserToolSettings;
import com.openforge.aimate.repository.AgentSessionRepository;
import com.openforge.aimate.repository.AgentToolRepository;
import com.openforge.aimate.websocket.AgentEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Agent's autonomous thinking loop.
 *
 * Loop shape (with long-term memory):
 *   while ACTIVE:
 *     1. RECALL    — search Milvus for memories relevant to current task
 *     2. PERCEIVE  — inject recalled memories into system prompt
 *     3. THINK     — stream LLM response, pushing each token to WebSocket
 *     4. DECIDE    — tool call? → ACT and loop; final answer? → REMEMBER + DONE
 *     5. ACT       — execute tool(s), append results to context
 *     6. PERSIST   — save context + iteration count to DB
 *     7. CHECK     — guard against external stop conditions (pause/terminate)
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
    // 不再对迭代轮次做硬性上限控制，由会话状态（PAUSED/TERMINATED）和外部中断来终止循环。
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
            - tavily_search: search the web for real-time or factual information. Use when the user asks about current events, news, or facts you need to look up.
            - create_tool: register a new tool (script-based: PYTHON_SCRIPT, NODE_SCRIPT, SHELL_CMD). Use when the user asks you to create a tool, add a capability, or write a reusable script/function that the agent can call later. Provide tool_name, tool_description, input_schema (JSON string), tool_type, and optionally script_content and entry_point.
            - install_container_package: install system packages (e.g. python3, nodejs) inside the user's isolated Linux container. Call this BEFORE running script tools that need an interpreter: the container starts as a minimal Linux and does not include python3/node by default. Use when a script tool fails with "command not found" (exit 127) or when you are about to run a Python/Node/shell script and have not installed the runtime yet. Pass "packages" as a space-separated list (e.g. "python3" or "python3 nodejs").
            - run_container_cmd: run short shell command(s) only (e.g. apt-get, chmod +x, mkdir). Do NOT pass a long heredoc or multi-line script here—it will be truncated. To create a script file, use write_container_file with content piped via stdin.
            - write_container_file: write content to a file in the user's container. path=container path (one line). content=file body (use \\n for newlines). append=true to append. Content is piped via stdin—no shell escaping. For very large scripts you can split into multiple calls with append=true,但一般情况下可以一次写完。
            
            Creating script files in the container (MANDATORY—do not suggest user to do it manually):
            - NEVER use run_container_cmd with cat/heredoc to write script content—it will be truncated and fail. NEVER suggest the user to "manually paste" or "run in terminal" to create the script; you MUST complete the task yourself.
            - To create any script file: use write_container_file only. For small/medium scripts, a single call with full content is fine. For extremely large scripts, you MAY split into multiple calls: first call with path+initial content, then same path+next content with append=true, until the whole script is written. Then run_container_cmd("chmod +x " + path). You must actually perform these calls until the file is complete—do not give up and ask the user to do it.
            
            Script execution environment (IMPORTANT):
            - 每个用户都有一个独立的 Linux 容器，视为该用户的“专属虚拟机环境”，不同用户的容器彼此完全隔离。
              默认镜像为精简 Debian（例如 debian:bookworm-slim），系统资源大致为：CPU 2 核、内存 4GB，根文件系统只读，工作目录挂载在 /home 之类的可写路径。
            - To run ANY command inside the user's container, you MUST use run_container_cmd only. There is no other way to execute commands in the container—do not assume other tools or APIs can run shell commands there; only run_container_cmd does.
            - When writing scripts that run long-running commands (npm install, apt-get, pip install, etc.): do NOT capture the subprocess stdout/stderr (e.g. in Python do not use capture_output=True or stdout=subprocess.PIPE for the long-running part). Let the subprocess write directly to the script's stdout so the user sees real-time output; otherwise the output is buffered and only appears when the command finishes.
            - Non-system script tools (those created with create_tool or custom scripts) run inside the same dedicated container of that user—not on the host. That environment is a minimal Linux; it does not pre-install python3, nodejs, etc. You MUST use install_container_package to install required runtimes (e.g. python3 for PYTHON_SCRIPT, nodejs for NODE_SCRIPT) before calling those script tools.
            - When a script tool returns "[ToolError] Script exited with code 127" (or the tool result contains "退出码 127" or "必须处理"), you MUST NOT report failure to the user yet. You MUST first call install_container_package with the appropriate package (python3 for Python scripts, nodejs for Node scripts, bash is usually present), wait for success, then call the same script tool again. Only if it still fails after installing and retrying may you explain the error to the user. Never skip the install-and-retry step when you see exit 127.
            
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
    private final UserToolSettingsService userToolSettingsService;
    private final SessionMessageService  sessionMessageService;
    private final SystemConfigService   systemConfigService;
    private final TavilySearchService   tavilySearchService;
    private final ScriptToolExecutor    scriptToolExecutor;
    private final UserContainerManager  userContainerManager;
    private final ScriptDockerProperties scriptDockerProperties;
    private final ExecutorService        agentVirtualThreadExecutor;
    private final ObjectMapper           objectMapper;
    private final UserApiKeyResolver     keyResolver;
    private final HttpClient             httpClient;
    private final LlmCallLogService      llmCallLogService;

    // Tracks which exact contents have already been stored via store_memory
    // in this JVM, per sessionId, to avoid spamming duplicate memories.
    private final Map<String, Set<String>> sessionStoredMemories =
            new ConcurrentHashMap<>();

    // ── Entry point ──────────────────────────────────────────────────────────

    /** 单条消息独立 run：绑定占位条 id，可多轮并行；中断仅影响该条。 */
    public void run(AgentSession session, long placeholderId) {
        ScopedValue.where(SESSION_SCOPE, session.getSessionId())
                .run(() -> executeLoopForPlaceholder(session, placeholderId));
    }

    /** 兼容入口：无 placeholder 时先确保消息表有首轮、创建占位，再按占位 run。 */
    public void run(AgentSession session) {
        ScopedValue.where(SESSION_SCOPE, session.getSessionId())
                .run(() -> executeLoop(session));
    }

    /**
     * 重试某条用户消息：用该条之前的上下文重新跑 AI，更新下一条 assistant 回复并写入新版本。
     * @param userMessageId 用户消息 id（该条之前的上下文 + 记忆作为 context）
     */
    public void runForRetry(AgentSession session, long userMessageId) {
        ScopedValue.where(SESSION_SCOPE, session.getSessionId())
                .run(() -> executeLoopForRetry(session, userMessageId));
    }

    // ── Main loop ────────────────────────────────────────────────────────────

    // ── Session-scoped LLM caller ────────────────────────────────────────────

    /**
     * Returns a caller that uses the user's own LLM key if configured,
     * otherwise delegates to the system-level LlmRouter (fallback).
     * 使用显式匿名类而不是 lambda，避免某些编译器对目标类型推断报
     * 「lambda 转换的目标类型必须为接口」之类的错误。
     */
    private java.util.function.BiFunction<ChatRequest, StreamCallbacks, ChatResponse>
    buildCaller(AgentSession session) {
        var cfgOpt = keyResolver.resolveDefaultLlm(session.getUserId());
        if (cfgOpt.isPresent()) {
            LlmProperties.ProviderConfig config = cfgOpt.get();
            LlmClient userClient = new LlmClient(httpClient, objectMapper, config);
            log.info("[Agent:{}] Using user key — provider={} model={}",
                    session.getSessionId(), config.name(), config.model());
            return new java.util.function.BiFunction<ChatRequest, StreamCallbacks, ChatResponse>() {
                @Override
                public ChatResponse apply(ChatRequest req, StreamCallbacks callbacks) {
                    long startNs = System.nanoTime();
                    ChatResponse response = userClient.streamChat(req, callbacks);
                    long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
                    try {
                        llmCallLogService.logSuccess(
                                config.name(),
                                config.model(),
                                LlmCallLog.CallType.AGENT_LOOP,
                                null,
                                "/chat/completions",
                                session.getUserId(),
                                session.getSessionId(),
                                latencyMs,
                                response
                        );
                    } catch (Exception e) {
                        log.warn("[Agent:{}] Failed to log LLM call: {}", session.getSessionId(), e.getMessage());
                    }
                    return response;
                }
            };
        } else {
            log.info("[Agent:{}] No user key found — using system LlmRouter", session.getSessionId());
            return new java.util.function.BiFunction<ChatRequest, StreamCallbacks, ChatResponse>() {
                @Override
                public ChatResponse apply(ChatRequest req, StreamCallbacks callbacks) {
                    long startNs = System.nanoTime();
                    ChatResponse response = llmRouter.streamChat(req, callbacks);
                    long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
                    try {
                        llmCallLogService.logSuccess(
                                "system_router",
                                response.model(),
                                LlmCallLog.CallType.AGENT_LOOP,
                                null,
                                "/chat/completions",
                                session.getUserId(),
                                session.getSessionId(),
                                latencyMs,
                                response
                        );
                    } catch (Exception e) {
                        log.warn("[Agent:{}] Failed to log LLM call: {}", session.getSessionId(), e.getMessage());
                    }
                    return response;
                }
            };
        }
    }

    private static final List<String> EXECUTION_PLAN = List.of("回忆", "思考与执行", "回答");

    /** 当模型只返回 reasoning 流、未返回 content 时的兜底展示文案（不写入完成记忆） */
    private static final String FALLBACK_EMPTY_ANSWER = "（模型未返回文字内容，仅返回了思考过程；可查看上方思考内容或重试。）";

    /** 按占位条执行单条回复：从消息表构建 context，可与其他条并行；中断仅影响本占位条。 */
    private void executeLoopForPlaceholder(AgentSession session, long placeholderId) {
        String sessionId = session.getSessionId();
        SessionMessage placeholder = sessionMessageService.findById(placeholderId)
                .orElseThrow(() -> new IllegalStateException("Placeholder not found: " + placeholderId));
        if (!SessionMessage.STATUS_ANSWERING.equals(placeholder.getMessageStatus())) {
            log.debug("[Agent:{}] Placeholder {} no longer ANSWERING, skip run.", sessionId, placeholderId);
            return;
        }
        log.info("[Agent:{}] Run for placeholder {} (message-level).", sessionId, placeholderId);

        try {
            session.setStatus(AgentSession.SessionStatus.ACTIVE);
            sessionRepository.save(session);
            publisher.publish(AgentEvent.statusChange(sessionId, "ACTIVE", SessionResponse.from(session)));

            var llmCaller = buildCaller(session);
            Long userId = session.getUserId();
            List<Message> context = sessionMessageService.loadContextForReply(session, placeholder);
            context = trimTrailingEmptyAssistant(context);

            publisher.publish(AgentEvent.planReady(sessionId, new ArrayList<>(EXECUTION_PLAN)));
            publisher.publish(AgentEvent.stepStart(sessionId, 1, EXECUTION_PLAN.get(0)));
            publisher.publish(AgentEvent.stepComplete(sessionId, 1, EXECUTION_PLAN.get(0), "已加载上下文"));
            publisher.publish(AgentEvent.stepStart(sessionId, 2, EXECUTION_PLAN.get(1)));

            RunResult result = runThinkAndActLoopForPlaceholder(session, sessionId, placeholderId, context, llmCaller, userId);

            publisher.publish(AgentEvent.stepComplete(sessionId, 2, EXECUTION_PLAN.get(1),
                    result.finalAnswer() != null ? "完成推理" : "达到最大迭代或已中断"));
            publisher.publish(AgentEvent.stepStart(sessionId, 3, EXECUTION_PLAN.get(2)));

            if (result.finalAnswer() != null || result.thinkingContent() != null) {
                String displayAnswer = (result.finalAnswer() != null && !result.finalAnswer().isBlank())
                        ? result.finalAnswer()
                        : (result.thinkingContent() != null && !result.thinkingContent().isEmpty() ? FALLBACK_EMPTY_ANSWER : "");
                sessionMessageService.saveNewVersion(placeholderId, displayAnswer, result.thinkingContent());
                session = sessionRepository.findBySessionId(sessionId).orElseThrow();
                session.setResult(displayAnswer);
                publisher.publish(AgentEvent.stepComplete(sessionId, 3, EXECUTION_PLAN.get(2), displayAnswer));
                publisher.publish(AgentEvent.finalAnswer(sessionId, displayAnswer, 0, placeholderId));
                if (!FALLBACK_EMPTY_ANSWER.equals(displayAnswer) && displayAnswer != null && !displayAnswer.isBlank()) {
                    storeCompletionMemory(sessionId, session.getTaskDescription(), displayAnswer, userId);
                }
            } else {
                sessionMessageService.updateAssistantMessage(placeholderId, null, SessionMessage.STATUS_INTERRUPTED);
            }

            session = sessionRepository.findBySessionId(sessionId).orElseThrow();
            if (!sessionMessageService.hasAnyAnswering(session.getId())) {
                session.setStatus(AgentSession.SessionStatus.IDLE);
                session.setCurrentAssistantMessageId(null);
                sessionRepository.save(session);
                publisher.publish(AgentEvent.statusChange(sessionId, "IDLE", SessionResponse.from(session)));
            }
        } catch (Exception e) {
            log.error("[Agent:{}] Exception in placeholder run {}: {}", sessionId, placeholderId, e.getMessage(), e);
            sessionMessageService.updateAssistantMessage(placeholderId, null, SessionMessage.STATUS_INTERRUPTED);
            session = sessionRepository.findBySessionId(sessionId).orElse(session);
            if (!sessionMessageService.hasAnyAnswering(session.getId())) {
                failSession(session, e.getMessage());
                publisher.publish(AgentEvent.statusChange(sessionId, "IDLE", SessionResponse.from(session)));
            }
        }
    }

    /**
     * 重试：用 user 消息之前的上下文重新生成下一条 assistant；若下一条已存在则更新内容并追加版本。
     */
    private void executeLoopForRetry(AgentSession session, long userMessageId) {
        String sessionId = session.getSessionId();
        SessionMessage userMsg = sessionMessageService.findById(userMessageId)
                .orElseThrow(() -> new IllegalStateException("User message not found: " + userMessageId));
        if (!"user".equals(userMsg.getRole())) {
            throw new IllegalArgumentException("Message " + userMessageId + " is not a user message");
        }
        int userSeq = userMsg.getSeq();
        SessionMessage assistantMsg = sessionMessageService.findBySessionAndSeq(session.getId(), userSeq + 1)
                .orElseThrow(() -> new IllegalStateException("No assistant reply found after user message seq " + userSeq));
        if (!"assistant".equals(assistantMsg.getRole())) {
            throw new IllegalStateException("Message at seq " + (userSeq + 1) + " is not assistant");
        }
        long assistantId = assistantMsg.getId();
        log.info("[Agent:{}] Retry for user msg {} → update assistant {}", sessionId, userMessageId, assistantId);

        sessionMessageService.updateAssistantMessage(assistantId, null, SessionMessage.STATUS_ANSWERING);

        try {
            session.setStatus(AgentSession.SessionStatus.ACTIVE);
            sessionRepository.save(session);
            publisher.publish(AgentEvent.statusChange(sessionId, "ACTIVE", SessionResponse.from(session)));

            var llmCaller = buildCaller(session);
            Long userId = session.getUserId();
            List<Message> context = sessionMessageService.loadContextUpToSeq(session, userSeq);
            context = trimTrailingEmptyAssistant(context);

            publisher.publish(AgentEvent.planReady(sessionId, new ArrayList<>(EXECUTION_PLAN)));
            publisher.publish(AgentEvent.stepStart(sessionId, 1, EXECUTION_PLAN.get(0)));
            publisher.publish(AgentEvent.stepComplete(sessionId, 1, EXECUTION_PLAN.get(0), "已加载上下文（重试）"));
            publisher.publish(AgentEvent.stepStart(sessionId, 2, EXECUTION_PLAN.get(1)));

            RunResult result = runThinkAndActLoopForPlaceholder(session, sessionId, assistantId, context, llmCaller, userId);

            publisher.publish(AgentEvent.stepComplete(sessionId, 2, EXECUTION_PLAN.get(1),
                    result.finalAnswer() != null ? "完成推理" : "达到最大迭代或已中断"));
            publisher.publish(AgentEvent.stepStart(sessionId, 3, EXECUTION_PLAN.get(2)));

            if (result.finalAnswer() != null || result.thinkingContent() != null) {
                String displayAnswer = (result.finalAnswer() != null && !result.finalAnswer().isBlank())
                        ? result.finalAnswer()
                        : (result.thinkingContent() != null && !result.thinkingContent().isEmpty() ? FALLBACK_EMPTY_ANSWER : "");
                sessionMessageService.saveNewVersion(assistantId, displayAnswer, result.thinkingContent());
                session = sessionRepository.findBySessionId(sessionId).orElseThrow();
                session.setResult(displayAnswer);
                publisher.publish(AgentEvent.stepComplete(sessionId, 3, EXECUTION_PLAN.get(2), displayAnswer));
                publisher.publish(AgentEvent.finalAnswer(sessionId, displayAnswer, 0, assistantId));
                if (!FALLBACK_EMPTY_ANSWER.equals(displayAnswer) && displayAnswer != null && !displayAnswer.isBlank()) {
                    storeCompletionMemory(sessionId, session.getTaskDescription(), displayAnswer, userId);
                }
            } else {
                sessionMessageService.updateAssistantMessage(assistantId, assistantMsg.getContent(), SessionMessage.STATUS_DONE);
            }

            session = sessionRepository.findBySessionId(sessionId).orElseThrow();
            if (!sessionMessageService.hasAnyAnswering(session.getId())) {
                session.setStatus(AgentSession.SessionStatus.IDLE);
                session.setCurrentAssistantMessageId(null);
                sessionRepository.save(session);
                publisher.publish(AgentEvent.statusChange(sessionId, "IDLE", SessionResponse.from(session)));
            }
        } catch (Exception e) {
            log.error("[Agent:{}] Exception in retry {}: {}", sessionId, userMessageId, e.getMessage(), e);
            sessionMessageService.updateAssistantMessage(assistantId, assistantMsg.getContent(), SessionMessage.STATUS_DONE);
            session = sessionRepository.findBySessionId(sessionId).orElse(session);
            if (!sessionMessageService.hasAnyAnswering(session.getId())) {
                failSession(session, e.getMessage());
                publisher.publish(AgentEvent.statusChange(sessionId, "IDLE", SessionResponse.from(session)));
            }
        }
    }

    /** 首轮或兼容入口：确保消息表有首条（system+user），创建占位，再按占位执行。 */
    private void executeLoop(AgentSession session) {
        String sessionId = session.getSessionId();
        log.info("[Agent:{}] Loop started. Task: {}", sessionId, session.getTaskDescription());
        session = sessionRepository.findBySessionId(sessionId).orElseThrow(() -> new IllegalStateException("Session vanished: " + sessionId));
        List<Message> existing = sessionMessageService.load(session);
        if (existing.isEmpty()) {
            String systemPrompt = buildSystemPrompt();
            sessionMessageService.replaceAll(session, List.of(
                    Message.system(systemPrompt),
                    Message.user(session.getTaskDescription())
            ));
        }
        SessionMessage placeholder = sessionMessageService.createPlaceholderAssistant(session);
        executeLoopForPlaceholder(session, placeholder.getId());
    }

    /**
     * Inner loop: think (stream) → tool calls or final answer.
     * Returns the final answer text, or null if max iterations reached.
     */
    private String runThinkAndActLoop(AgentSession session, String sessionId,
                                      java.util.function.BiFunction<ChatRequest, StreamCallbacks, ChatResponse> llmCaller,
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
            if (session.getStatus() != AgentSession.SessionStatus.ACTIVE && session.getStatus() != AgentSession.SessionStatus.RUNNING) {
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

            StreamCallbacks callbacks = new StreamCallbacks(
                    token -> publisher.publish(AgentEvent.thinking(sessionId, token, finalIter)),
                    t -> publisher.publish(AgentEvent.thinking(sessionId, t, finalIter))
            );
            ChatResponse response = llmCaller.apply(request, callbacks);

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
                    String toolResult = executeTool(toolCall, sessionId, userId, finalIter);
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
        }
    }

    /** 单条回复的 think 循环返回：最终回答 + 思考过程（供入库展示）。 */
    private record RunResult(String finalAnswer, String thinkingContent) {}

    /**
     * 单条回复的 think 循环：context 来自消息表且可变，追加写回 appendToRun；每轮检查占位条是否被中断。
     * 使用 StreamCallbacks：reasoning 流（如 DeepSeek reasoning_content）→ THINKING 事件 + thinking_content 入库；
     * content 流仅作为最终回答，不写入思考。若 LLM 不返回 reasoning_content，思考为空。
     */
    private RunResult runThinkAndActLoopForPlaceholder(AgentSession session, String sessionId, long placeholderId,
                                                       List<Message> context,
                                                       java.util.function.BiFunction<ChatRequest, StreamCallbacks, ChatResponse> llmCaller,
                                                       Long userId) {
        StringBuilder thinkingContent = new StringBuilder();
        int iteration = 0;
        Set<String> toolsCreatedThisRun = new java.util.LinkedHashSet<>();
        while (true) {
            SessionMessage p = sessionMessageService.findById(placeholderId).orElse(null);
            if (p == null || !SessionMessage.STATUS_ANSWERING.equals(p.getMessageStatus())) {
                log.info("[Agent:{}] Placeholder {} interrupted or gone, exit loop.", sessionId, placeholderId);
                return new RunResult(null, thinkingContent.isEmpty() ? null : thinkingContent.toString());
            }

            iteration++;
            publisher.publish(AgentEvent.iterationStart(sessionId, iteration));

            String queryForTools = lastUserMessageFrom(context, session.getTaskDescription());
            List<Tool> tools = loadRelevantTools(queryForTools, TOP_K_TOOLS, userId);
            java.util.Set<String> haveNames = new java.util.HashSet<>();
            for (Tool t : tools) haveNames.add(t.function().name());
            for (String name : toolsCreatedThisRun) {
                if (!haveNames.contains(name)) {
                    toolRepository.findByToolName(name).map(this::toTool).ifPresent(t -> {
                        tools.add(t);
                        haveNames.add(name);
                    });
                }
            }
            ChatRequest request = ChatRequest.withTools("", context, tools.isEmpty() ? null : tools);
            final int finalIter = iteration;
            StringBuilder currentIterReasoning = new StringBuilder();

            StreamCallbacks callbacks = new StreamCallbacks(
                    t -> { /* content 仅由 LlmClient 累积并作为 firstMessage().content() 返回，不推思考 */ },
                    t -> {
                        publisher.publish(AgentEvent.thinking(sessionId, t, finalIter));
                        currentIterReasoning.append(t);
                        thinkingContent.append(t);
                    }
            );
            ChatResponse response = llmCaller.apply(request, callbacks);

            Message assistantMessage = response.firstMessage();

            if (response.hasToolCalls()) {
                List<Message> toAppend = new ArrayList<>();
                Message assistantWithReasoning = Message.builder()
                        .role("assistant")
                        .content(assistantMessage.content())
                        .toolCalls(assistantMessage.toolCalls())
                        .reasoningContent(currentIterReasoning.isEmpty() ? "" : currentIterReasoning.toString())
                        .build();
                toAppend.add(assistantWithReasoning);
                for (ToolCall toolCall : assistantMessage.toolCalls()) {
                    publisher.publish(AgentEvent.toolCall(sessionId, toolCall, finalIter));
                    String toolResult = executeTool(toolCall, sessionId, userId, finalIter);
                    if ("create_tool".equals(toolCall.function().name()) && toolResult != null && !toolResult.startsWith("[ToolError]")) {
                        String createdName = parseToolNameFromCreateToolArgs(toolCall.function().arguments());
                        if (createdName != null && !createdName.isBlank()) {
                            toolsCreatedThisRun.add(createdName);
                            toolIndexService.indexToolByName(createdName, userId);
                        }
                    }
                    publisher.publish(AgentEvent.toolResult(
                            sessionId, toolCall.function().name(), toolResult, finalIter));
                    toAppend.add(Message.toolResult(toolCall.id(), toolResult));
                    maybeRememberToolResult(sessionId, toolCall.function().name(), toolResult, userId);

                    // 为历史查看构建「思考+工具」统一时间线：在思考文本中插入精简的工具调用摘要
                    appendToolSummaryToThinking(thinkingContent, toolCall, toolResult, finalIter);
                }
                context.addAll(toAppend);
                sessionMessageService.appendToRun(session, placeholderId, toAppend.toArray(new Message[0]));
            } else {
                String answer = (assistantMessage.content() != null && !assistantMessage.content().isBlank())
                        ? assistantMessage.content() : "";
                // 部分模型（如 DeepSeek 思考模式）可能只返回 reasoning 流、不返回 content 流，导致 content 为空
                if (answer.isEmpty() && !thinkingContent.isEmpty()) {
                    log.debug("[Agent:{}] LLM returned empty content but had reasoning ({} chars), using fallback.", sessionId, thinkingContent.length());
                    answer = FALLBACK_EMPTY_ANSWER;
                }
                return new RunResult(answer, thinkingContent.isEmpty() ? null : thinkingContent.toString());
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
     * 将一次工具调用的关键信息以简洁文本形式附加到思考内容中，
     * 便于前端在历史「思考」面板中按时间线查看「思考 + 工具调用」的整体过程。
     */
    private void appendToolSummaryToThinking(StringBuilder thinking,
                                             ToolCall toolCall,
                                             String toolResult,
                                             int iteration) {
        if (thinking == null || toolCall == null) return;
        String name = toolCall.function() != null ? toolCall.function().name() : "";
        String args = toolCall.function() != null && toolCall.function().arguments() != null
                ? toolCall.function().arguments() : "";
        thinking.append("\n\n[工具调用 第 ").append(iteration).append(" 轮] ")
                .append(name).append("(\n  args=")
                .append(truncateForThinking(args, 320))
                .append("\n)");
        if (toolResult != null && !toolResult.isBlank()) {
            thinking.append("\n[工具结果摘要] ")
                    .append(truncateForThinking(toolResult, 480));
        }
    }

    /** 思考内容中的工具摘要专用截断，避免把长日志完整塞进思考里。 */
    private String truncateForThinking(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...(truncated)";
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

    private String executeTool(ToolCall toolCall, String sessionId, Long userId, int iteration) {
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
        // Built-in: tavily_search — web search via Tavily API (key from system_config)
        if ("tavily_search".equals(toolName)) {
            return executeTavilySearchTool(arguments);
        }
        // Built-in: create_tool — 编写工具的工具，将新工具注册到 agent_tools 表
        if ("create_tool".equals(toolName)) {
            return executeCreateTool(arguments);
        }
        // Built-in: install_container_package — 在用户隔离的 Linux 容器内安装软件（如 python3、nodejs）
        if ("install_container_package".equals(toolName)) {
            return executeInstallContainerPackage(sessionId, arguments, userId, toolCall.id(), iteration);
        }
        // Built-in: run_container_cmd — 在用户容器内执行任意 shell 命令（如安装 JDK、配置环境）
        if ("run_container_cmd".equals(toolName)) {
            return executeRunContainerCmd(sessionId, arguments, userId, toolCall.id(), iteration);
        }
        // Built-in: write_container_file — 通过 stdin 写内容到容器内文件，不经过 shell 转义，支持任意符号
        if ("write_container_file".equals(toolName)) {
            return executeWriteContainerFile(sessionId, arguments, userId);
        }

        AgentTool tool = toolRepository.findByToolName(toolName).orElse(null);
        if (tool == null) return "[ToolError] Unknown tool: " + toolName;

        return switch (tool.getToolType()) {
            case JAVA_NATIVE  -> executeJavaTool(tool, arguments, sessionId);
            case PYTHON_SCRIPT, NODE_SCRIPT, SHELL_CMD ->
                    executeScriptTool(tool, arguments, sessionId, userId, toolCall.id(), iteration);
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

    /**
     * Built-in tavily_search tool: web search via Tavily API.
     * API Key 从系统配置表 system_config 的 TAVILY_API_KEY 读取。
     */
    private String executeTavilySearchTool(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String query = args.path("query").asText("");
            int maxResults = args.path("max_results").asInt(5);
            String searchDepth = args.path("search_depth").asText("basic");
            String topic = args.path("topic").asText("general");
            String apiKey = systemConfigService.getTavilyApiKey().orElse(null);
            return tavilySearchService.search(apiKey, query, maxResults, searchDepth, topic);
        } catch (Exception e) {
            log.warn("[Agent] tavily_search failed: {}", e.getMessage());
            return "[ToolError] tavily_search failed: " + e.getMessage();
        }
    }

    /**
     * Built-in create_tool: 编写工具的工具。将新工具注册到 agent_tools 表，仅支持脚本类型（PYTHON_SCRIPT / NODE_SCRIPT / SHELL_CMD）。
     * 执行逻辑仍为占位，后续可接入真实脚本执行引擎。
     */
    private String executeCreateTool(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String toolName = args.path("tool_name").asText("").trim();
            String toolDescription = args.path("tool_description").asText("").trim();
            String inputSchemaRaw = args.path("input_schema").asText("").trim();
            String toolTypeStr = args.path("tool_type").asText("").toUpperCase();
            String scriptContent = args.has("script_content") ? args.path("script_content").asText("") : "";
            String entryPoint = args.has("entry_point") ? args.path("entry_point").asText("").trim() : null;

            if (toolName.isEmpty()) return "[ToolError] create_tool 需要非空的 tool_name（仅字母、数字、下划线，如 get_weather）。";
            if (!toolName.matches("[a-zA-Z][a-zA-Z0-9_]*")) return "[ToolError] tool_name 须以字母开头，仅含字母、数字、下划线。";
            if (Set.of("recall_memory", "store_memory", "tavily_search", "create_tool", "install_container_package", "run_container_cmd", "write_container_file").contains(toolName)) {
                return "[ToolError] tool_name 不能与内置工具重名: " + toolName;
            }
            if (toolDescription.isEmpty()) return "[ToolError] create_tool 需要非空的 tool_description。";
            if (inputSchemaRaw.isEmpty()) return "[ToolError] create_tool 需要非空的 input_schema（JSON 对象字符串）。";

            AgentTool.ToolType toolType;
            try {
                toolType = AgentTool.ToolType.valueOf(toolTypeStr);
            } catch (Exception e) {
                return "[ToolError] tool_type 须为 PYTHON_SCRIPT、NODE_SCRIPT 或 SHELL_CMD 之一。";
            }
            if (toolType == AgentTool.ToolType.JAVA_NATIVE) {
                return "[ToolError] 不允许通过 create_tool 创建 JAVA_NATIVE 工具，仅支持 PYTHON_SCRIPT / NODE_SCRIPT / SHELL_CMD。";
            }
            if (scriptContent == null) scriptContent = "";
            if (entryPoint == null || entryPoint.isEmpty()) {
                String ext = switch (toolType) {
                    case PYTHON_SCRIPT -> ".py";
                    case NODE_SCRIPT -> ".js";
                    case SHELL_CMD -> ".sh";
                    default -> "";
                };
                entryPoint = toolName + ext;
            }

            objectMapper.readTree(inputSchemaRaw);

            AgentTool entity = toolRepository.findByToolName(toolName).orElse(null);
            if (entity != null) {
                entity.setToolDescription(toolDescription);
                entity.setInputSchema(inputSchemaRaw);
                entity.setToolType(toolType);
                entity.setScriptContent(scriptContent.isEmpty() ? null : scriptContent);
                entity.setEntryPoint(entryPoint);
                entity.setIsActive(true);
                toolRepository.save(entity);
                log.info("[Agent] create_tool: 已更新工具 {}", toolName);
                return "工具已更新: " + toolName + "。后续对话中可调用此工具。";
            }
            entity = AgentTool.builder()
                    .toolName(toolName)
                    .toolDescription(toolDescription)
                    .inputSchema(inputSchemaRaw)
                    .toolType(toolType)
                    .scriptContent(scriptContent.isEmpty() ? null : scriptContent)
                    .entryPoint(entryPoint)
                    .isActive(true)
                    .build();
            toolRepository.save(entity);
            log.info("[Agent] create_tool: 已注册新工具 {}", toolName);
            return "工具已注册: " + toolName + "。后续对话中可调用此工具。注意：脚本类工具当前为占位执行，实际运行需接入执行引擎。";
        } catch (JsonProcessingException e) {
            return "[ToolError] input_schema 须为合法 JSON 对象: " + e.getMessage();
        } catch (Exception e) {
            log.warn("[Agent] create_tool failed: {}", e.getMessage());
            return "[ToolError] create_tool 执行失败: " + e.getMessage();
        }
    }

    /**
     * 在用户隔离的 Linux 容器内执行 apt-get install，供 AI 在运行脚本前安装 python3、nodejs 等。
     * 仅当 Docker 启用且 userId 存在时可用；容器需具备网络（apt-get update）。
     */
    /**
     * 在用户容器内执行 apt-get 安装；输出实时推送到前端（TOOL_OUTPUT_CHUNK），与 run_container_cmd/脚本一致。
     */
    private String executeInstallContainerPackage(String sessionId, String argumentsJson, Long userId, String toolCallId, int iteration) {
        if (!scriptDockerProperties.enabled()) {
            return "[ToolError] install_container_package 仅在启用 Docker 用户隔离时可用。请在配置中开启 agent.script.docker.enabled。";
        }
        if (userId == null) {
            return "[ToolError] install_container_package 需要已登录用户（无法确定用户容器）。";
        }
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String packagesStr = args.path("packages").asText("").trim();
            if (packagesStr.isEmpty()) {
                return "[ToolError] install_container_package 需要非空的 packages 参数，例如 \"python3\" 或 \"python3 nodejs\"。";
            }
            List<String> packages = new ArrayList<>();
            for (String s : packagesStr.split("[,\\s]+")) {
                String p = s.trim();
                if (p.isEmpty()) continue;
                if (!p.matches("[a-zA-Z0-9.+-]+")) {
                    return "[ToolError] 非法包名: " + p + "。仅允许字母、数字、点、加号、连字符。";
                }
                packages.add(p);
            }
            if (packages.isEmpty()) {
                return "[ToolError] install_container_package 需要至少一个合法包名，例如 \"python3\"。";
            }
            String containerIdOrName = userContainerManager.getOrCreateContainer(userId);
            if (containerIdOrName == null) {
                return "[ToolError] 无法获取或创建用户 Linux 容器，请确认 Docker 已启动。";
            }
            String pkgList = String.join(" ", packages);
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "exec", containerIdOrName,
                    "sh", "-c", "apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y " + pkgList
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder outBuilder = new StringBuilder();
            AtomicLong lastOutputTime = new AtomicLong(System.currentTimeMillis());
            Thread reader = new Thread(() -> readProcessOutputWithStreaming(
                    outBuilder, p.getInputStream(), 50_000,
                    lastOutputTime, chunk -> publisher.publish(AgentEvent.toolOutputChunk(sessionId, toolCallId, chunk, iteration))
            ));
            reader.start();
            int idleSec = scriptDockerProperties.runContainerCmdIdleTimeoutSeconds();
            long idleMs = idleSec * 1000L;
            while (p.isAlive() && !p.waitFor(500, TimeUnit.MILLISECONDS)) {
                if (p.isAlive() && (System.currentTimeMillis() - lastOutputTime.get() >= idleMs)) {
                    p.destroyForcibly();
                    try { reader.join(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    return "[ToolError] install_container_package 在 " + idleSec + "s 内无新输出，视为卡住已终止。\n" + (outBuilder.length() > 800 ? outBuilder.substring(0, 800) + "..." : outBuilder.toString());
                }
            }
            try { reader.join(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            String output = outBuilder.toString().trim();
            int exit = p.exitValue();
            if (exit != 0) {
                return "[ToolError] 容器内安装失败 (exit " + exit + "): " + (output.length() > 1500 ? output.substring(0, 1500) + "..." : output);
            }
            log.info("[Agent:{}] install_container_package 已安装: {}", sessionId, pkgList);
            return "已在该用户 Linux 容器内安装: " + pkgList + "。\n" + (output.length() > 800 ? output.substring(0, 800) + "..." : output);
        } catch (Exception e) {
            log.warn("[Agent:{}] install_container_package failed: {}", sessionId, e.getMessage());
            return "[ToolError] install_container_package 执行失败: " + e.getMessage();
        }
    }

    private static final int RUN_CONTAINER_CMD_MAX_OUTPUT_CHARS = 8000;

    /**
     * 在用户隔离的 Linux 容器内执行任意 shell 命令。实时推送 stdout 到前端（TOOL_OUTPUT_CHUNK），
     * 超时策略：有输出则重置「空闲计时」；仅当连续无输出超过 idleTimeout 或总时长超过 maxTimeout 才判超时。
     */
    private String executeRunContainerCmd(String sessionId, String argumentsJson, Long userId, String toolCallId, int iteration) {
        if (!scriptDockerProperties.enabled()) {
            return "[ToolError] run_container_cmd 仅在启用 Docker 用户隔离时可用。";
        }
        if (userId == null) {
            return "[ToolError] run_container_cmd 需要已登录用户（无法确定用户容器）。";
        }
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            List<String> commandsToRun = new ArrayList<>();
            // 提前捕获常见误用：传入 cmd 字段而非 command/commands
            if (args.has("cmd") && !args.has("command") && !args.has("commands")) {
                return "[ToolError] run_container_cmd 参数错误：请使用 \"command\"（单条命令字符串）或 \"commands\"（字符串数组），不要使用 \"cmd\" 字段。示例：{\"command\":\"node --version\"} 或 {\"commands\":[\"apt-get update\",\"apt-get install -y nodejs\"]}。";
            }
            if (args.has("commands") && args.get("commands").isArray()) {
                for (JsonNode c : args.get("commands")) {
                    String s = c.asText("").trim();
                    if (!s.isEmpty()) commandsToRun.add(s);
                }
            }
            if (commandsToRun.isEmpty()) {
                String single = args.path("command").asText("").trim();
                if (!single.isEmpty()) commandsToRun.add(single);
            }
            if (commandsToRun.isEmpty()) {
                return "[ToolError] run_container_cmd 需要非空的 command 或 commands（字符串数组）参数。";
            }
                for (String cmd : commandsToRun) {
                if (cmd.length() > 800) {
                    return "[ToolError] run_container_cmd 单条命令不得超过 800 字符（当前 " + cmd.length() + "）。长脚本请使用 write_container_file 写入脚本文件，再通过 run_container_cmd 执行脚本。";
                }
            }
            String containerIdOrName = userContainerManager.getOrCreateContainer(userId);
            if (containerIdOrName == null) {
                return "[ToolError] 无法获取或创建用户 Linux 容器，请确认 Docker 已启动。";
            }
            int idleSec = scriptDockerProperties.runContainerCmdIdleTimeoutSeconds();
            long idleMs = idleSec * 1000L;
            StringBuilder allOutput = new StringBuilder();
            for (int i = 0; i < commandsToRun.size(); i++) {
                String command = commandsToRun.get(i);
                ProcessBuilder pb = new ProcessBuilder(
                        "docker", "exec", containerIdOrName,
                        "sh", "-c", command
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();
                StringBuilder outBuilder = new StringBuilder();
                AtomicLong lastOutputTime = new AtomicLong(System.currentTimeMillis());
                Thread reader = new Thread(() -> readProcessOutputWithStreaming(
                        outBuilder, p.getInputStream(), RUN_CONTAINER_CMD_MAX_OUTPUT_CHARS,
                        lastOutputTime, chunk -> publisher.publish(AgentEvent.toolOutputChunk(sessionId, toolCallId, chunk, iteration))
                ));
                reader.start();
                // 不设总时长：仅当连续无输出超过 idleSec 才视为卡住；长时间有输出（如几小时下载）会一直跑
                while (p.isAlive() && !p.waitFor(500, TimeUnit.MILLISECONDS)) {
                    if (p.isAlive()) {
                        long now = System.currentTimeMillis();
                        long sinceLastOutput = now - lastOutputTime.get();
                        if (sinceLastOutput >= idleMs) {
                            p.destroyForcibly();
                            try { reader.join(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                            String output = outBuilder.toString().trim();
                            return "[ToolError] 第 " + (i + 1) + "/" + commandsToRun.size() + " 条命令在 " + idleSec + "s 内无新输出，视为卡住已终止。\n已捕获输出:\n" + (allOutput.length() > 1500 ? allOutput.substring(0, 1500) + "..." : allOutput) + "\n" + (output.length() > 500 ? output.substring(0, 500) + "..." : output);
                        }
                    }
                }
                try { reader.join(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                String output = outBuilder.toString().trim();
                int exit = p.exitValue();
                if (exit != 0) {
                    return "[ToolError] 第 " + (i + 1) + "/" + commandsToRun.size() + " 条命令退出码 " + exit + ":\n" + (output.length() > 3000 ? output.substring(0, 3000) + "..." : output);
                }
                if (!output.isEmpty()) {
                    if (allOutput.length() > 0) allOutput.append("\n");
                    allOutput.append(output);
                }
            }
            log.info("[Agent:{}] run_container_cmd 执行成功, {} 条命令", sessionId, commandsToRun.size());
            String result = allOutput.toString().trim();
            return result.isEmpty() ? "(共 " + commandsToRun.size() + " 条命令执行成功，无输出)" : result;
        } catch (Exception e) {
            log.warn("[Agent:{}] run_container_cmd failed: {}", sessionId, e.getMessage());
            return "[ToolError] run_container_cmd 执行失败: " + e.getMessage();
        }
    }

    /** 读取进程输出并按行/块推送；每有内容就更新 lastOutputTime，供「无输出即卡住」超时判断。 */
    private void readProcessOutputWithStreaming(StringBuilder sb, java.io.InputStream in, int maxChars,
                                                 AtomicLong lastOutputTime, java.util.function.Consumer<String> onChunk) {
        try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null && sb.length() < maxChars) {
                sb.append(line).append("\n");
                lastOutputTime.set(System.currentTimeMillis());
                if (!line.isEmpty()) {
                    try { onChunk.accept(line + "\n"); } catch (Exception ignored) { }
                }
            }
            if (sb.length() >= maxChars) sb.append("\n... (输出已截断)");
        } catch (Exception ignored) { }
    }

    /**
     * 标准做法：通过 stdin 将内容管道进容器写文件，不经过 shell 参数，故无需转义任何符号（引号、$、反引号等）。
     * 容器内执行 read -r path 读第一行作为路径，cat > "$path" 或 cat >> "$path" 写剩余 stdin。
     * 单次 content 不得超过 WRITE_CONTAINER_FILE_MAX_CONTENT_CHARS，长脚本分多次调用、append=true 追加。
     */
    private String executeWriteContainerFile(String sessionId, String argumentsJson, Long userId) {
        if (!scriptDockerProperties.enabled()) {
            return "[ToolError] write_container_file 仅在启用 Docker 用户隔离时可用。";
        }
        if (userId == null) {
            return "[ToolError] write_container_file 需要已登录用户。";
        }
        Path tempFile = null;
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String path = args.path("path").asText("").trim();
            String content = args.has("content") ? args.get("content").asText("") : "";
            boolean append = args.path("append").asBoolean(false);
            if (path.isEmpty()) {
                return "[ToolError] write_container_file 需要非空的 path（容器内文件路径，不要包含换行）。";
            }
            if (path.contains("\n") || path.contains("\r")) {
                return "[ToolError] write_container_file 的 path 不能包含换行符。";
            }
            // 不再对 content 人为限制长度；如脚本极大，可由调用方自行按需拆分多次写入。
            String containerIdOrName = userContainerManager.getOrCreateContainer(userId);
            if (containerIdOrName == null) {
                return "[ToolError] 无法获取或创建用户 Linux 容器。";
            }
            tempFile = Files.createTempFile("aimate_wcf_", ".dat");
            Files.writeString(tempFile, path + "\n" + content, StandardCharsets.UTF_8);
            String shellCmd = append ? "read -r path; cat >> \"$path\"" : "read -r path; cat > \"$path\"";
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "exec", "-i", containerIdOrName,
                    "sh", "-c", shellCmd
            );
            pb.redirectInput(ProcessBuilder.Redirect.from(tempFile.toFile()));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder outBuilder = new StringBuilder();
            Thread reader = new Thread(() -> readProcessOutputInto(outBuilder, p.getInputStream(), 4000));
            reader.start();
            boolean finished = p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            try { reader.join(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            if (!finished) {
                p.destroyForcibly();
                return "[ToolError] write_container_file 超时（60s）。";
            }
            int exit = p.exitValue();
            if (exit != 0) {
                return "[ToolError] write_container_file 失败 exit " + exit + ": " + outBuilder.toString().trim();
            }
            log.info("[Agent:{}] write_container_file 已写入 {} (append={})", sessionId, path, append);
            return "已写入容器内文件: " + path + " (" + (append ? "追加" : "覆盖") + "), " + content.length() + " 字节。";
        } catch (Exception e) {
            log.warn("[Agent:{}] write_container_file failed: {}", sessionId, e.getMessage());
            return "[ToolError] write_container_file 执行失败: " + e.getMessage();
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (Exception ignored) { }
            }
        }
    }

    private static void readProcessOutputInto(StringBuilder sb, java.io.InputStream in, int maxChars) {
        try (var r = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
            char[] buf = new char[1024];
            int n;
            while ((n = r.read(buf)) != -1 && sb.length() < maxChars) sb.append(buf, 0, n);
            if (sb.length() >= maxChars) sb.append("\n... (输出已截断)");
        } catch (Exception ignored) { }
    }

    private static String readProcessOutput(Process p) {
        try (var r = new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** 从 create_tool 的 arguments JSON 中解析出 tool_name，用于本轮循环内刷新工具列表。 */
    private String parseToolNameFromCreateToolArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) return null;
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String name = args.path("tool_name").asText(null);
            return (name != null && !name.isBlank()) ? name.trim() : null;
        } catch (Exception e) {
            return null;
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

    private String executeScriptTool(AgentTool tool, String arguments, String sessionId, Long userId, String toolCallId, int iteration) {
        log.debug("[Agent:{}] Executing script tool '{}' type={}",
                sessionId, tool.getToolName(), tool.getToolType());
        return scriptToolExecutor.execute(tool, arguments, userId,
                chunk -> publisher.publish(AgentEvent.toolOutputChunk(sessionId, toolCallId, chunk, iteration)));
    }

    // ── Tool loading (vectorized: only pass semantically relevant tools) ─────

    /**
     * Returns the last user message content from context, or taskDescription if none.
     * Used as the query for tool retrieval.
     */
    /** 发给 LLM 前去掉末尾的「空占位 assistant」，避免 API 报 content/tool_calls 必填。 */
    private List<Message> trimTrailingEmptyAssistant(List<Message> context) {
        if (context == null || context.isEmpty()) return context;
        Message last = context.get(context.size() - 1);
        if ("assistant".equals(last.role())
                && (last.content() == null || last.content().isBlank())
                && (last.toolCalls() == null || last.toolCalls().isEmpty())) {
            return new ArrayList<>(context.subList(0, context.size() - 1));
        }
        return new ArrayList<>(context);
    }

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
     * Respects user tool settings (memory, web search, create_tool, script exec).
     */
    private List<Tool> loadRelevantTools(String queryText, int topK, Long userId) {
        List<String> relevantIds = toolIndexService.searchRelevantTools(queryText, topK, userId);
        if (relevantIds == null || relevantIds.isEmpty()) {
            return loadAllTools(userId);
        }
        List<Tool> fromSearch = new ArrayList<>();
        Set<String> added = new java.util.LinkedHashSet<>();
        for (String id : relevantIds) {
            if (id == null || !added.add(id)) continue;
            toolRepository.findByToolName(id).map(this::toTool).ifPresent(fromSearch::add);
        }
        return ensureSystemToolsFirst(fromSearch, userId);
    }

    /** 从 agent_tools 表加载所有启用工具；若表为空则退回内置三件套。按用户工具开关过滤系统工具。 */
    private List<Tool> loadAllTools(Long userId) {
        List<Tool> base = toolRepository.findByIsActiveTrue().stream()
                .map(this::toTool)
                .toList();
        List<Tool> result = new ArrayList<>(base);
        if (result.isEmpty()) {
            log.debug("[Agent] agent_tools 表为空，使用内置工具（可执行 seed_builtin_tools.sql 将内置工具入库）");
        }
        return ensureSystemToolsFirst(result, userId);
    }

    private static final Set<String> SYSTEM_TOOL_NAMES_SET = Set.of("recall_memory", "store_memory", "tavily_search");
    /** 由代码直接构建、不从 DB 重复加载的工具名 */
    private static final Set<String> CODE_BUILT_TOOL_NAMES = Set.of("recall_memory", "store_memory", "tavily_search", "create_tool", "install_container_package", "run_container_cmd", "write_container_file");

    /**
     * 按用户工具开关组装修复：长期记忆、联网搜索、AI 编写工具、脚本执行。
     * 将启用的系统工具置于列表前，再追加 DB 中非内置工具。
     */
    private List<Tool> ensureSystemToolsFirst(List<Tool> tools, Long userId) {
        UserToolSettings settings = userToolSettingsService.getOrCreate(userId);
        boolean memoryEnabled = settings == null || Boolean.TRUE.equals(settings.getMemoryEnabled());
        boolean webSearchEnabled = settings == null || Boolean.TRUE.equals(settings.getWebSearchEnabled());
        boolean createToolEnabled = settings == null || Boolean.TRUE.equals(settings.getCreateToolEnabled());
        boolean scriptExecEnabled = settings == null || Boolean.TRUE.equals(settings.getScriptExecEnabled());

        List<Tool> out = new ArrayList<>();
        if (memoryEnabled) {
            out.add(buildRecallMemoryTool());
            out.add(buildStoreMemoryTool());
        }
        if (webSearchEnabled) {
            out.add(buildTavilySearchTool());
        }
        for (Tool t : tools) {
            if (!CODE_BUILT_TOOL_NAMES.contains(t.function().name())) out.add(t);
        }
        if (scriptExecEnabled) {
            out.add(buildInstallContainerPackageTool());
            out.add(buildRunContainerCmdTool());
            out.add(buildWriteContainerFileTool());
        }
        if (createToolEnabled) {
            out.add(buildCreateToolTool());
        }
        return out;
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

    private Tool buildTavilySearchTool() {
        String schema = """
                {
                  "type":"object",
                  "properties":{
                    "query":{
                      "type":"string",
                      "description":"Search query in natural language (e.g. 'Spring Boot 3.2 release notes', '今日天气'). Use when you need real-time or factual information from the web."
                    },
                    "max_results":{
                      "type":"integer",
                      "minimum":1,
                      "maximum":20,
                      "description":"Max number of search results to return (default 5)."
                    },
                    "search_depth":{
                      "type":"string",
                      "enum":["basic","advanced","fast","ultra-fast"],
                      "description":"basic=balanced; advanced=higher relevance; fast/ultra-fast=lower latency. Default: basic."
                    },
                    "topic":{
                      "type":"string",
                      "enum":["general","news","finance"],
                      "description":"Search category. general=default; news=recent events; finance=financial. Default: general."
                    }
                  },
                  "required":["query"]
                }
                """;
        try {
            JsonNode params = objectMapper.readTree(schema);
            return Tool.ofFunction(new ToolFunction(
                    "tavily_search",
                    "Search the web for real-time or factual information via Tavily. Use when the user asks about current events, recent news, or facts you are unsure about. Prefer recall_memory for the user's own stored information.",
                    params
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build tavily_search tool schema", e);
        }
    }

    private Tool buildInstallContainerPackageTool() {
        String schema = """
                {
                  "type":"object",
                  "properties":{
                    "packages":{
                      "type":"string",
                      "description":"Space- or comma-separated list of Debian/apt package names to install in the user's Linux container. E.g. 'python3' for Python scripts, 'nodejs' for Node scripts. Install runtimes BEFORE calling script tools that need them."
                    }
                  },
                  "required":["packages"]
                }
                """;
        try {
            JsonNode params = objectMapper.readTree(schema);
            return Tool.ofFunction(new ToolFunction(
                    "install_container_package",
                    "Install system packages (e.g. python3, nodejs) inside the user's isolated Linux container. Call this BEFORE running script tools that need an interpreter—the container is minimal and does not include python3/node by default. Use when a script fails with exit 127 (command not found) or when you are about to run a Python/Node script. Example: packages='python3' or 'python3 nodejs'.",
                    params
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build install_container_package schema", e);
        }
    }

    private Tool buildRunContainerCmdTool() {
        String schema = """
                {
                  "type":"object",
                  "properties":{
                    "command":{
                      "type":"string",
                      "description":"Single shell command to run (e.g. apt-get install -y openjdk-17-jdk). Use for short one-off commands."
                    },
                    "commands":{
                      "type":"array",
                      "items":{"type":"string"},
                      "description":"List of shell commands to run in sequence. PREFERRED for long scripts: one command per line, e.g. echo 'line1' > file, echo 'line2' >> file, to avoid truncation."
                    }
                  }
                }
                """;
        try {
            JsonNode params = objectMapper.readTree(schema);
            return Tool.ofFunction(new ToolFunction(
                    "run_container_cmd",
                    "Run shell command(s) in the user's Linux container. This is the ONLY way to execute any command inside the container—use this tool for all in-container commands. Use 'command' for a single short command. Use 'commands' (array of strings) for long scripts: pass one command per line. Timeout per command is configurable (default 300s).",
                    params
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build run_container_cmd schema", e);
        }
    }

    private Tool buildWriteContainerFileTool() {
        String schema = """
                {
                  "type":"object",
                  "properties":{
                    "path":{
                      "type":"string",
                      "description":"Absolute or relative path of the file inside the container (e.g. /home/workspace/scripts/foo.sh). Must be a single line, no newlines."
                    },
                    "content":{
                      "type":"string",
                      "description":"File content for this chunk. Use \\n for newlines. For extremely large scripts, you MAY split into multiple calls and use append=true, but typical scripts can be written in a single call. No shell escaping needed."
                    },
                    "append":{
                      "type":"boolean",
                      "description":"If true, append to existing file; otherwise overwrite. Default false."
                    }
                  },
                  "required":["path","content"]
                }
                """;
        try {
            JsonNode params = objectMapper.readTree(schema);
            return Tool.ofFunction(new ToolFunction(
                    "write_container_file",
                    "Write content to a file in the user's container. path=container path. content is arbitrary length text (use \\n for newlines). append=true to append. For very large scripts you MAY split into multiple calls (first with path+initial content, then same path+next content+append=true) until done. Then run_container_cmd chmod +x path.",
                    params
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build write_container_file schema", e);
        }
    }

    /** 编写工具的工具：AI 可调用此工具将新工具注册到 agent_tools 表（仅支持脚本类型）。 */
    private Tool buildCreateToolTool() {
        String schema = """
                {
                  "type":"object",
                  "properties":{
                    "tool_name":{
                      "type":"string",
                      "description":"Unique identifier for the new tool (letters, numbers, underscores only; e.g. get_weather, calc_sum). Must start with a letter."
                    },
                    "tool_description":{
                      "type":"string",
                      "description":"Natural language description for when the LLM should call this tool. Be clear and specific."
                    },
                    "input_schema":{
                      "type":"string",
                      "description":"JSON Schema object as a string. Example: { \\\"type\\\": \\\"object\\\", \\\"properties\\\": { \\\"city\\\": { \\\"type\\\": \\\"string\\\" } }, \\\"required\\\": [ \\\"city\\\" ] }. Must be valid JSON."
                    },
                    "tool_type":{
                      "type":"string",
                      "enum":["PYTHON_SCRIPT","NODE_SCRIPT","SHELL_CMD"],
                      "description":"Script type: PYTHON_SCRIPT, NODE_SCRIPT, or SHELL_CMD. JAVA_NATIVE is not allowed via this tool."
                    },
                    "script_content":{
                      "type":"string",
                      "description":"Source code of the script. Optional for registration; can be filled later. For PYTHON_SCRIPT use Python; NODE_SCRIPT use JavaScript; SHELL_CMD use shell commands."
                    },
                    "entry_point":{
                      "type":"string",
                      "description":"Suggested filename (e.g. get_weather.py). Optional; defaults to tool_name + extension."
                    }
                  },
                  "required":["tool_name","tool_description","input_schema","tool_type"]
                }
                """;
        try {
            JsonNode params = objectMapper.readTree(schema);
            return Tool.ofFunction(new ToolFunction(
                    "create_tool",
                    "Create or update a new tool that the agent can call later. Use when the user asks you to 'create a tool', 'add a capability', 'write a tool for X', or to implement a reusable script/function. You must provide tool_name (unique id), tool_description (when to use it), input_schema (JSON Schema string), and tool_type (PYTHON_SCRIPT, NODE_SCRIPT, or SHELL_CMD). Optionally provide script_content (source code) and entry_point (filename). Only script-based tools can be created; JAVA_NATIVE is not allowed.",
                    params
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build create_tool schema", e);
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
        session.setStatus(AgentSession.SessionStatus.IDLE);
        session.setErrorMessage(reason);
        sessionRepository.save(session);
        publisher.publish(AgentEvent.statusChange(session.getSessionId(), "IDLE", SessionResponse.from(session)));
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
