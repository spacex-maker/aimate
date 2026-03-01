package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 单次工具调用的展示信息，供前端在消息上方与思考内容一起展示。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ToolCallDisplayDto(
        @JsonProperty("name") String name,
        @JsonProperty("arguments") String arguments,
        @JsonProperty("result") String result
) {}
