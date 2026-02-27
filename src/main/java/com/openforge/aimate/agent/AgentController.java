package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.SessionResponse;
import com.openforge.aimate.agent.dto.StartSessionRequest;
import com.openforge.aimate.domain.AgentSession;
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

import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * REST API for Agent session lifecycle management.
 *
 * Endpoints:
 *   POST   /api/agent/sessions            — create and immediately start a new session
 *   GET    /api/agent/sessions/{id}       — poll current session status / result
 *   POST   /api/agent/sessions/{id}/pause — pause (loop idles between iterations)
 *   POST   /api/agent/sessions/{id}/resume— resume a paused session
 *   DELETE /api/agent/sessions/{id}       — abort and mark as FAILED
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
    private final AgentSessionRepository sessionRepository;
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
        Long userId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) {
            userId = id;
        }

        AgentSession session = AgentSession.builder()
                .userId(userId)
                .sessionId(sessionId)
                .taskDescription(request.task())
                .status(AgentSession.SessionStatus.PENDING)
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

    // ── Status query ─────────────────────────────────────────────────────────

    /**
     * Poll the current state of a session.
     * Can also be used to retrieve the final result once status=COMPLETED.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable String sessionId) {
        AgentSession session = findOrThrow(sessionId);
        return ResponseEntity.ok(SessionResponse.from(session));
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
        if (session.getStatus() != AgentSession.SessionStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Session is not RUNNING, current status: " + session.getStatus());
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
        session.setStatus(AgentSession.SessionStatus.RUNNING);
        sessionRepository.save(session);
        log.info("[Controller] Resumed session {}", sessionId);
        return ResponseEntity.ok(SessionResponse.from(session));
    }

    // ── Abort ────────────────────────────────────────────────────────────────

    /**
     * Abort a session.  Sets status to FAILED so the loop exits on its next
     * iteration check.  Idempotent — calling it on an already-terminal session
     * is a no-op.
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> abortSession(@PathVariable String sessionId) {
        AgentSession session = findOrThrow(sessionId);
        if (session.getStatus() == AgentSession.SessionStatus.COMPLETED
                || session.getStatus() == AgentSession.SessionStatus.FAILED) {
            return ResponseEntity.ok(SessionResponse.from(session));
        }
        session.setStatus(AgentSession.SessionStatus.FAILED);
        session.setErrorMessage("Aborted by user");
        sessionRepository.save(session);
        log.info("[Controller] Aborted session {}", sessionId);
        return ResponseEntity.ok(SessionResponse.from(session));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private AgentSession findOrThrow(String sessionId) {
        return sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Session not found: " + sessionId));
    }
}
