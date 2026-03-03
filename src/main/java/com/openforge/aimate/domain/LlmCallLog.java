package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistent log of LLM calls for cost/usage analytics.
 *
 *  - One row per logical LLM call (chat / streamChat / compress, etc.).
 *  - Captures provider/model, token usage, latency and basic context (user/session/tool).
 */
@Getter
@Setter
@Entity
@Table(name = "llm_call_logs")
public class LlmCallLog extends BaseEntity {

    /** Which provider handled the call: openai / deepseek / system_router / user_custom / etc. */
    @Column(nullable = false, length = 64)
    private String provider;

    /** Concrete model name used, e.g. gpt-4o / deepseek-chat. */
    @Column(nullable = false, length = 128)
    private String model;

    /** High-level purpose of the call: AGENT_LOOP / MEMORY_COMPRESS / OTHER. */
    @Enumerated(EnumType.STRING)
    @Column(name = "call_type", nullable = false, length = 32)
    private CallType callType = CallType.OTHER;

    /** Optional tool name / feature name, e.g. recall_memory, store_memory, compress_memory. */
    @Column(name = "tool_name", length = 64)
    private String toolName;

    /** Which HTTP endpoint was hit, e.g. /v1/chat/completions. */
    @Column(name = "endpoint", length = 128)
    private String endpoint;

    /** Optional user id (null for system-level or anonymous calls). */
    @Column(name = "user_id")
    private Long userId;

    /** Optional agent session id (用于排查某次对话消耗). */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** Milliseconds spent from request send to response fully received. */
    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    /** Estimated cost in USD（可选，按需要事后补算）. */
    @Column(name = "estimated_cost_usd")
    private Double estimatedCostUsd;

    /** HTTP status code from provider (e.g. 200 / 401 / 500). */
    @Column(name = "http_status")
    private Integer httpStatus;

    /** Provider-specific error code, if any. */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    /** Short error message (截断存储). */
    @Column(name = "error_message", length = 512)
    private String errorMessage;

    /** Raw provider request id header when available, e.g. OpenAI request-id. */
    @Column(name = "request_id", length = 128)
    private String requestId;

    /** True if call succeeded; false when any error/exception occurred. */
    @Column(name = "success", nullable = false)
    private boolean success = true;

    public enum CallType {
        AGENT_LOOP,
        MEMORY_COMPRESS,
        OTHER
    }
}

