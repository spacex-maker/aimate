package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 管理员更新系统配置请求体：允许修改配置值与描述。
 */
public record UpdateSystemConfigRequest(
        @JsonProperty("configValue") String configValue,
        @JsonProperty("description") String description
) {
}

