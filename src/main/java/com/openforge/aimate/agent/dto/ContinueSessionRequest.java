package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request body for continuing an existing Agent session with a new
 * user message (multi-turn conversation).
 *
 * 支持携带模型选择信息，避免「切换模型请求」与「发消息请求」的竞态：
 * - 若同时传 source/systemModelId/userApiKeyId，则本次请求优先使用该模型，
 *   并更新 user_default_models 记录。
 */
public record ContinueSessionRequest(

        String message,

        @JsonProperty("modelSource")
        String modelSource,      // "SYSTEM" | "USER_KEY" | null

        @JsonProperty("systemModelId")
        Long systemModelId,

        @JsonProperty("userApiKeyId")
        Long userApiKeyId,

        @JsonProperty("images")
        List<String> images
) {}

