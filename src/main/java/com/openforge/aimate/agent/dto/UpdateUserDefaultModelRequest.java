package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 前端聊天输入框切换模型时，用于告知后端用户当前首选模型。
 * source = SYSTEM 时仅传 systemModelId；
 * source = USER_KEY 时仅传 userApiKeyId。
 */
public record UpdateUserDefaultModelRequest(
        @JsonProperty("source")       String source,
        @JsonProperty("systemModelId") Long systemModelId,
        @JsonProperty("userApiKeyId") Long userApiKeyId
) {}

