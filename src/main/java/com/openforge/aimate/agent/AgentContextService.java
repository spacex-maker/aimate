package com.openforge.aimate.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.repository.AgentSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the Agent's context window — the ordered list of Messages that is
 * sent to the LLM on every iteration.
 *
 * Responsibilities:
 *   - Deserialize context from AgentSession.contextWindow (JSON string in DB)
 *   - Append new messages (assistant reply, tool results) after each iteration
 *   - Persist the updated context back to the DB
 *   - Trim the context when it approaches the token limit (sliding window)
 *
 * Why store context in the DB?
 *   The Java process is intentionally stateless.  Storing context here means
 *   any restart — scheduled, crash, or blue-green swap — picks up exactly
 *   where the Agent left off.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentContextService {

    /**
     * Safety ceiling: keep at most this many messages in the context window.
     * At ~500 tokens/message average this is roughly 25K tokens.
     * Tune this based on the model's actual context limit.
     */
    private static final int MAX_CONTEXT_MESSAGES = 50;

    private static final TypeReference<List<Message>> MESSAGE_LIST_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper             objectMapper;
    private final AgentSessionRepository   sessionRepository;

    // ── Load ─────────────────────────────────────────────────────────────────

    /**
     * Deserialize and return the current context window for a session.
     * Returns an empty list if the context has not been initialized yet.
     */
    public List<Message> load(AgentSession session) {
        String json = session.getContextWindow();
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(json, MESSAGE_LIST_TYPE));
        } catch (JsonProcessingException e) {
            log.error("[Context] Failed to deserialize context for session {}: {}",
                    session.getSessionId(), e.getMessage());
            return new ArrayList<>();
        }
    }

    // ── Append ───────────────────────────────────────────────────────────────

    /**
     * Append one or more messages to the session's context window and
     * immediately persist the updated context to the DB.
     *
     * Performs an in-memory trim before persisting to avoid unbounded growth.
     */
    @Transactional
    public void append(AgentSession session, Message... messages) {
        List<Message> context = load(session);
        for (Message m : messages) {
            context.add(m);
        }
        trim(context);
        persist(session, context);
    }

    /**
     * Replace the entire context window (used when initializing a session
     * with a system prompt and the first user message).
     */
    @Transactional
    public void initialize(AgentSession session, List<Message> messages) {
        List<Message> context = new ArrayList<>(messages);
        trim(context);
        persist(session, context);
    }

    // ── Trim ─────────────────────────────────────────────────────────────────

    /**
     * Sliding-window trim: always keep the system prompt (first message if
     * role="system"), then keep the most recent (MAX_CONTEXT_MESSAGES - 1)
     * messages so the Agent never loses its persona.
     */
    private void trim(List<Message> context) {
        if (context.size() <= MAX_CONTEXT_MESSAGES) return;

        Message systemPrompt = null;
        if (!context.isEmpty() && "system".equals(context.get(0).role())) {
            systemPrompt = context.get(0);
        }

        // Keep tail
        int fromIndex = context.size() - (MAX_CONTEXT_MESSAGES - (systemPrompt != null ? 1 : 0));
        List<Message> trimmed = new ArrayList<>(context.subList(fromIndex, context.size()));

        if (systemPrompt != null) {
            trimmed.add(0, systemPrompt);
        }

        context.clear();
        context.addAll(trimmed);
        log.debug("[Context] Trimmed context to {} messages", context.size());
    }

    // ── Persist ──────────────────────────────────────────────────────────────

    /**
     * Persist context to DB. Always reloads the session by id so we work with the
     * current row (and version), avoiding ObjectOptimisticLockingFailureException
     * when the session reference passed in is detached or stale (e.g. from another
     * transaction or thread).
     */
    private void persist(AgentSession session, List<Message> context) {
        try {
            String json = objectMapper.writeValueAsString(context);
            AgentSession current = sessionRepository.findById(session.getId())
                    .orElseThrow(() -> new IllegalStateException("Session not found: " + session.getSessionId()));
            current.setContextWindow(json);
            sessionRepository.save(current);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize context for session "
                    + session.getSessionId(), e);
        }
    }
}
