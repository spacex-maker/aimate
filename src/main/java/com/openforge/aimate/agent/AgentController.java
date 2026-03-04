package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.AssistantVersionDto;
import com.openforge.aimate.agent.dto.ChatMessageDto;
import com.openforge.aimate.agent.dto.ContinueSessionRequest;
import com.openforge.aimate.agent.dto.DeleteSessionRequest;
import com.openforge.aimate.agent.dto.RetryRequest;
import com.openforge.aimate.agent.dto.SessionResponse;
import com.openforge.aimate.agent.dto.StartSessionRequest;
import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.memory.LongTermMemoryService;
import com.openforge.aimate.repository.AgentSessionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * REST API for Agent session lifecycle management.
 *
 * Endpoints:
 *   POST   /api/agent/sessions              — create and immediately start a new session
 *   GET    /api/agent/sessions              — list recent sessions for current user (?limit=10)
 *   GET    /api/agent/sessions/{id}         — poll current session status / result
 *   POST   /api/agent/sessions/{id}/pause   — pause (loop idles between iterations)
 *   POST   /api/agent/sessions/{id}/resume  — resume a paused session
 *   POST   /api/agent/sessions/{id}/continue— continue a completed session with a new user message
 *   DELETE /api/agent/sessions/{id}         — abort and mark as FAILED
 *
 * WebSocket subscription (returned in the create response):
 *   Frontend connects to ws://host/ws, subscribes to /topic/agent/{sessionId},
 *   and receives real-time AgentEvent frames while the loop runs.
 *
 * Concurrency:
 *   Each session loop runs in a dedicated virtual thread started here.
 *   The ExecutorService is our `agentVirtualThreadExecutor` from AppConfig —
 *   virtual threads are essentially free, so thousands of concurrent sessions
 *   are supported without any thread-pool tuning.
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/sessions")
@RequiredArgsConstructor
public class AgentController {

    private final AgentLoopService       agentLoopService;
    private final SessionMessageService  sessionMessageService;
    private final AgentSessionRepository sessionRepository;
    private final LongTermMemoryService  longTermMemoryService;
    private final ExecutorService        agentVirtualThreadExecutor;

    // ── Create & Start ───────────────────────────────────────────────────────

    /**
     * Create a new Agent session and immediately launch its thinking loop.
     *
     * Response contains the sessionId and the WebSocket topic path so the
     * frontend can subscribe before the first thinking event arrives.
     *
     * HTTP 201 Created on success.
     */
    @PostMapping
    public ResponseEntity<SessionResponse> startSession(
            @Valid @RequestBody StartSessionRequest request) {

        String sessionId = request.sessionId() != null && !request.sessionId().isBlank()
                ? request.sessionId()
                : UUID.randomUUID().toString();

        if (sessionRepository.findBySessionId(sessionId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Session already exists: " + sessionId);
        }

        // Extract userId from JWT principal (set by JwtAuthFilter)
        Long userId = getCurrentUserId();

        AgentSession session = AgentSession.builder()
                .userId(userId)
                .sessionId(sessionId)
                .taskDescription(request.task())
                .status(AgentSession.SessionStatus.IDLE)
                .iterationCount(0)
                .build();
        session = sessionRepository.save(session);

        // Launch the loop on a virtual thread — non-blocking for the HTTP thread
        final AgentSession finalSession = session;
        agentVirtualThreadExecutor.submit(() -> {
            try {
                agentLoopService.run(finalSession);
            } catch (Exception e) {
                log.error("[Controller] Uncaught exception in loop for session {}: {}",
                        sessionId, e.getMessage(), e);
            }
        });

        log.info("[Controller] Started session {} — task: {}",
                sessionId, request.task().substring(0, Math.min(80, request.task().length())));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SessionResponse.from(session));
    }

    // ── List recent sessions (for home page) ─────────────────────────────────

    /**
     * List recent sessions for the current user, ordered by create time descending.
     * Used by the frontend "最近会话" list. Requires auth; returns empty list if no userId.
     */
    @GetMapping
    public ResponseEntity<List<SessionResponse>> listRecentSessions(
            @RequestParam(defaultValue = "10") int limit) {
        Long userId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) userId = id;
        if (userId == null) return ResponseEntity.ok(Collections.emptyList());
        limit = Math.min(Math.max(1, limit), 50);
        List<AgentSession> sessions = sessionRepository.findByUserIdAndHiddenFalseOrderByCreateTimeDesc(
                userId, PageRequest.of(0, limit));
        List<SessionResponse> list = sessions.stream()
                .map(SessionResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ── Status query ─────────────────────────────────────────────────────────

    /**
     * Poll the current state of a session.
     * Can also be used to retrieve the latest result (session.result) when status=IDLE.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable String sessionId) {
        AgentSession session = findOrThrow(sessionId);
        return ResponseEntity.ok(SessionResponse.from(session));
    }

    /**
     * Load conversation history for a session (from agent_session_messages).
     * Used by the frontend to display past messages when opening an existing session.
     */
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<ChatMessageDto>> getSessionMessages(@PathVariable String sessionId) {
        AgentSession session = findOrThrow(sessionId);
        return ResponseEntity.ok(sessionMessageService.loadDtos(session));
    }

    // ── Pause ────────────────────────────────────────────────────────────────

    /**
     * Pause a running session.
     *
     * The loop checks the status at the top of each iteration.  The currently
     * executing LLM call or tool call will finish before the pause takes effect.
     * No data is lost — context is already persisted to DB.
     */
    @PostMapping("/{sessionId}/pause")
    public ResponseEntity<SessionResponse> pauseSession(@PathVariable String sessionId) {
        AgentSession session = findOrThrow(sessionId);
        if (session.getStatus() != AgentSession.SessionStatus.ACTIVE && session.getStatus() != AgentSession.SessionStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Session is not ACTIVE, current status: " + session.getStatus());
        }
        session.setStatus(AgentSession.SessionStatus.PAUSED);
        sessionRepository.save(session);
        log.info("[Controller] Paused session {}", sessionId);
        return ResponseEntity.ok(SessionResponse.from(session));
    }

    // ── Resume ───────────────────────────────────────────────────────────────

    /**
     * Resume a paused session.
     *
     * The loop's spin-wait detects the status change within ~2 seconds
     * and continues from the next iteration.
     */
    @PostMapping("/{sessionId}/resume")
    public ResponseEntity<SessionResponse> resumeSession(@PathVariable String sessionId) {
        AgentSession session = findOrThrow(sessionId);
        if (session.getStatus() != AgentSession.SessionStatus.PAUSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Session is not PAUSED, current status: " + session.getStatus());
        }
        session.setStatus(AgentSession.SessionStatus.ACTIVE);
        sessionRepository.save(session);
        log.info("[Controller] Resumed session {}", sessionId);
        return ResponseEntity.ok(SessionResponse.from(session));
    }

    // ── Abort ────────────────────────────────────────────────────────────────

    /**
     * Abort a session.  Sets status to IDLE so the loop exits on its next
     * iteration check.  Idempotent when already IDLE.
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> abortSession(@PathVariable String sessionId) {
        AgentSession session = findOrThrow(sessionId);
        if (session.getStatus() == AgentSession.SessionStatus.IDLE || session.getStatus() == AgentSession.SessionStatus.COMPLETED
                || session.getStatus() == AgentSession.SessionStatus.FAILED || session.getStatus() == AgentSession.SessionStatus.PENDING) {
            return ResponseEntity.ok(SessionResponse.from(session));
        }
        session.setStatus(AgentSession.SessionStatus.IDLE);
        session.setErrorMessage("Aborted by user");
        sessionRepository.save(session);
        log.info("[Controller] Aborted session {}", sessionId);
        return ResponseEntity.ok(SessionResponse.from(session));
    }

    /**
     * 删除/清理会话。
     *
     * 选项说明：
     * - hideOnly       = true  时，仅将会话标记为隐藏，不删除任何数据（聊天记录与长期记忆都保留）；
     * - deleteMessages = true  时，删除会话内所有消息记录，并清空上下文、结果等字段；
     * - deleteMemories = true  时，删除该会话在长期记忆中的所有记录。
     *
     * 当 hideOnly=true 时，将忽略 deleteMessages / deleteMemories。
     * 若三者均为 false，将返回 400。
     */
    @PostMapping("/{sessionId}/delete")
    public ResponseEntity<SessionResponse> deleteSession(
            @PathVariable String sessionId,
            @RequestBody DeleteSessionRequest request
    ) {
        AgentSession session = findOrThrow(sessionId);
        Long userId = getCurrentUserId();

        if (request.hideOnly()) {
            session.setHidden(true);
            sessionRepository.save(session);
            log.info("[Controller] Hidden session {}", sessionId);
            return ResponseEntity.ok(SessionResponse.from(session));
        }

        if (!request.deleteMessages() && !request.deleteMemories()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "至少选择删除聊天记录、删除长期记忆或仅隐藏会话中的一项");
        }

        if (request.deleteMessages()) {
            sessionMessageService.deleteAllForSession(session);
            session.setContextWindow(null);
            session.setIterationCount(0);
            session.setResult(null);
            session.setErrorMessage(null);
            session.setCurrentAssistantMessageId(null);
        }

        if (request.deleteMemories()) {
            longTermMemoryService.deleteBySession(session.getSessionId(), userId);
        }

        session.setHidden(true);
        sessionRepository.save(session);
        log.info("[Controller] Deleted session data for {} (messages={}, memories={})",
                sessionId, request.deleteMessages(), request.deleteMemories());
        return ResponseEntity.ok(SessionResponse.from(session));
    }

    // ── Continue (multi-turn) ────────────────────────────────────────────────
    /**
     * 用户随时可发消息：立即追加到消息表并启动一条新回复（可与上一条并行）；是否等待上一条由前端/参数决定。
     */
    @PostMapping("/{sessionId}/continue")
    public ResponseEntity<SessionResponse> continueSession(
            @PathVariable String sessionId,
            @Valid @RequestBody ContinueSessionRequest request
    ) {
        AgentSession session = findOrThrow(sessionId);

        sessionMessageService.append(session, Message.user(request.message()));
        com.openforge.aimate.domain.SessionMessage placeholder = sessionMessageService.createPlaceholderAssistant(session);
        session.setErrorMessage(null);
        sessionRepository.save(session);

        final AgentSession finalSession = sessionRepository.findBySessionId(sessionId).orElseThrow();
        final long placeholderId = placeholder.getId();
        agentVirtualThreadExecutor.submit(() -> {
            try {
                agentLoopService.run(finalSession, placeholderId);
            } catch (Exception e) {
                log.error("[Controller] Uncaught exception in loop for session {} (continue): {}",
                        sessionId, e.getMessage(), e);
            }
        });

        log.info("[Controller] Continued session {} with new user message (placeholder {}).", sessionId, placeholderId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(SessionResponse.from(finalSession));
    }

    /**
     * 消息级中断：指定某条 assistant 消息 id，将其标为已中断，对应 run 在下一轮检查时退出。
     */
    @PostMapping("/{sessionId}/interrupt")
    public ResponseEntity<SessionResponse> interruptSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) java.util.Map<String, Long> body
    ) {
        AgentSession session = findOrThrow(sessionId);
        Long assistantMessageId = body != null ? body.get("assistantMessageId") : null;
        if (assistantMessageId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assistantMessageId required");
        }
        sessionMessageService.updateAssistantMessage(assistantMessageId, null, com.openforge.aimate.domain.SessionMessage.STATUS_INTERRUPTED);
        session = sessionRepository.findBySessionId(sessionId).orElseThrow();
        if (!sessionMessageService.hasAnyAnswering(session.getId())) {
            session.setStatus(AgentSession.SessionStatus.IDLE);
            session.setCurrentAssistantMessageId(null);
            session.setErrorMessage("用户中断");
            sessionRepository.save(session);
        }
        log.info("[Controller] Interrupted message {} in session {}", assistantMessageId, sessionId);
        return ResponseEntity.ok(SessionResponse.from(session));
    }

    /**
     * 重试某条用户消息：用该条之前的上下文重新跑 AI，更新下一条 assistant 内容并追加新版本。
     */
    @PostMapping("/{sessionId}/retry")
    public ResponseEntity<SessionResponse> retryMessage(
            @PathVariable String sessionId,
            @RequestBody RetryRequest request
    ) {
        AgentSession session = findOrThrow(sessionId);
        if (request.getUserMessageId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userMessageId required");
        }
        final long userMessageId = request.getUserMessageId();
        final AgentSession finalSession = sessionRepository.findBySessionId(sessionId).orElseThrow();
        agentVirtualThreadExecutor.submit(() -> {
            try {
                agentLoopService.runForRetry(finalSession, userMessageId);
            } catch (Exception e) {
                log.error("[Controller] Uncaught exception in retry for session {}: {}",
                        sessionId, e.getMessage(), e);
            }
        });
        log.info("[Controller] Retry for user message {} in session {}", userMessageId, sessionId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(SessionResponse.from(finalSession));
    }

    /**
     * 某条 assistant 回复的历史版本列表（版本号降序），供前端「查看历史版本」。
     */
    @GetMapping("/{sessionId}/messages/{messageId}/versions")
    public ResponseEntity<List<AssistantVersionDto>> getMessageVersions(
            @PathVariable String sessionId,
            @PathVariable Long messageId
    ) {
        findOrThrow(sessionId);
        List<AssistantVersionDto> list = sessionMessageService.getVersions(messageId).stream()
                .map(v -> new AssistantVersionDto(
                        v.getVersion(),
                        v.getContent(),
                        v.getCreateTime() != null ? v.getCreateTime().toString() : null
                ))
                .toList();
        return ResponseEntity.ok(list);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private AgentSession findOrThrow(String sessionId) {
        return sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Session not found: " + sessionId));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) {
            return id;
        }
        return null;
    }
}
