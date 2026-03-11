package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 聊天输入框模型选择所需的完整数据：
 * - 用户自有模型列表（user_api_keys，key_type=LLM）；
 * - 系统模型列表（system_models，enabled=true，按 sort_order 排序）；
 * - 当前用户首选模型（user_default_models）。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record AvailableModelsDto(
        @JsonProperty("userModels")   List<UserModelDto> userModels,
        @JsonProperty("systemModels") List<SystemModelDto> systemModels,
        @JsonProperty("defaultModel") UserDefaultModelDto defaultModel
) {}

