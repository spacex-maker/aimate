package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 删除/清理会话时由前端提交的选项：
 * - deleteMessages: 是否删除会话内聊天记录（消息与上下文）
 * - deleteMemories: 是否删除会话关联的长期记忆
 * - hideOnly:       是否仅隐藏会话（若为 true，将忽略前两项，不做任何物理删除）
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record DeleteSessionRequest(

        @JsonProperty("deleteMessages") boolean deleteMessages,
        @JsonProperty("deleteMemories") boolean deleteMemories,
        @JsonProperty("hideOnly")       boolean hideOnly
) {
}

