package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 管理员更新系统模型启用状态请求体。
 */
public record UpdateSystemModelEnabledRequest(
        @JsonProperty("enabled") boolean enabled
) {}
