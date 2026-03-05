package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openforge.aimate.domain.User;

import java.time.LocalDateTime;

/**
 * 管理员用户列表项，不包含密码等敏感信息；camelCase。
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record AdminUserListItemDto(
        @JsonProperty("id")           long id,
        @JsonProperty("username")     String username,
        @JsonProperty("email")       String email,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("status")     String status,
        @JsonProperty("role")        String role,
        @JsonProperty("createTime")  LocalDateTime createTime,
        @JsonProperty("lastLoginTime") LocalDateTime lastLoginTime
) {
    public static AdminUserListItemDto from(User u) {
        if (u == null || u.getId() == null) return null;
        return new AdminUserListItemDto(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getDisplayName(),
                u.getStatus() != null ? u.getStatus().name() : null,
                u.getRole() != null ? u.getRole().name() : null,
                u.getCreateTime(),
                u.getLastLoginTime()
        );
    }
}
