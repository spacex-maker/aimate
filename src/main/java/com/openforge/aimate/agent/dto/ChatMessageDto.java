package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 单条对话消息，供前端展示历史用。
 * messageStatus 仅 assistant 使用：ANSWERING=回答中, DONE=已完成, INTERRUPTED=已中断。
 * thinkingContent、toolCalls 仅 assistant 使用，在消息上方展示（思考 + 工具调用）。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ChatMessageDto(
        @JsonProperty("id") Long id,           // 主键，assistant 条用于中断时传 assistantMessageId
        @JsonProperty("role")   String role,   // user | assistant | tool
        @JsonProperty("content") String content,
        @JsonProperty("messageStatus") String messageStatus,  // null | ANSWERING | DONE | INTERRUPTED
        @JsonProperty("thinkingContent") String thinkingContent,  // 仅 assistant：思考过程，前端可折叠展示
        @JsonProperty("toolCalls") List<ToolCallDisplayDto> toolCalls,  // 仅 assistant：该条回复过程中的工具调用列表，前端可折叠展示
        @JsonProperty("createTime") String createTime  // 消息创建时间，前端底部展示时间戳
) {
    public ChatMessageDto(Long id, String role, String content, String messageStatus) {
        this(id, role, content, messageStatus, null, null, null);
    }

    public ChatMessageDto(Long id, String role, String content, String messageStatus, String thinkingContent) {
        this(id, role, content, messageStatus, thinkingContent, null, null);
    }

    public ChatMessageDto(String role, String content, String messageStatus) {
        this(null, role, content, messageStatus, null, null, null);
    }
}
