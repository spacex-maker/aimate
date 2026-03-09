package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openforge.aimate.llm.model.ToolCall;

/**
 * 用于历史回放的「思考+工具调用」Block，与前端 StreamBlock 类型保持字段对齐。
 *
 * kind:
 *  - iteration    number, id
 *  - thinking     content, complete, id
 *  - toolCall     call, result, streamingOutput?, durationMs?, id
 *  - finalAnswer  content, id
 *  - error        message, id
 *
 * 目前后端仅构建 thinking / toolCall / finalAnswer 三种，iteration/error 预留未来使用。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record StreamBlockDto(
        @JsonProperty("kind") String kind,
        @JsonProperty("number") Integer number,
        @JsonProperty("id") String id,
        @JsonProperty("content") String content,
        @JsonProperty("call") ToolCall call,
        @JsonProperty("result") String result,
        @JsonProperty("streamingOutput") String streamingOutput,
        @JsonProperty("message") String message,
        // thinking 专用：是否已完整
        @JsonProperty("complete") Boolean complete,
        // toolCall 专用：执行耗时（毫秒）
        @JsonProperty("durationMs") Long durationMs
) {
}

