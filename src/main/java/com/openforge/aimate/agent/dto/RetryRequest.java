package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 重试请求：指定要重试的用户消息 id，用该条之前的上下文重新生成下一条 assistant。
 *  同时允许携带本次希望使用的模型选择信息，避免用户在前端切换模型后，
 *  重试请求仍然使用旧的默认模型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetryRequest {

    @JsonProperty("userMessageId")
    private Long userMessageId;

    @JsonProperty("modelSource")
    private String modelSource;      // "SYSTEM" | "USER_KEY" | null

    @JsonProperty("systemModelId")
    private Long systemModelId;

    @JsonProperty("userApiKeyId")
    private Long userApiKeyId;
}
