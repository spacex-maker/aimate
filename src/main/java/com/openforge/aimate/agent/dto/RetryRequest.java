package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 重试请求：指定要重试的用户消息 id，用该条之前的上下文重新生成下一条 assistant。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetryRequest {

    @JsonProperty("userMessageId")
    private Long userMessageId;
}
