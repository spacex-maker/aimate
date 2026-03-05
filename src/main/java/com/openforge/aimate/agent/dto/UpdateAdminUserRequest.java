package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 管理员更新用户请求体：可选更新状态、角色。
 */
public record UpdateAdminUserRequest(
        @JsonProperty("status") String status,
        @JsonProperty("role")  String role
) {}
