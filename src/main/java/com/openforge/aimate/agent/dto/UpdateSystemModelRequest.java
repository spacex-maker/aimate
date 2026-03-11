package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 管理员更新系统模型属性请求体：可更新启用状态与排序权重。
 */
public record UpdateSystemModelRequest(
        @JsonProperty("enabled")   Boolean enabled,
        @JsonProperty("sortOrder") Integer sortOrder
) {}

