package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request body for continuing an existing Agent session with a new
 * user message (multi-turn conversation).
 *
 * 支持携带模型选择与思考开关，避免「切换思考/模型」与「发消息」的竞态：
 * - 若同时传 source/systemModelId/userApiKeyId，则本次请求优先使用该模型，并更新 user_default_models；
 * - thinkingEnabled 随本条消息生效（写入偏好 + 本轮 ScopedValue），不必等上一次 PUT 提交。
 */
public record ContinueSessionRequest(

        String message,

        @JsonProperty("modelSource")
        String modelSource,      // "SYSTEM" | "USER_KEY" | null

        @JsonProperty("systemModelId")
        Long systemModelId,

        @JsonProperty("userApiKeyId")
        Long userApiKeyId,

        @JsonProperty("thinkingEnabled")
        Boolean thinkingEnabled,

        @JsonProperty("images")
        List<String> images
) {}
