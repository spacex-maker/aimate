package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 管理员批量操作用户：启用 / 禁用 / 系统赠送充值。
 */
public record BatchAdminUsersRequest(
        @JsonProperty("userIds") List<Long> userIds,
        /** ENABLE | DISABLE | RECHARGE */
        @JsonProperty("action") String action,
        /** RECHARGE 时必填，正整数积分 */
        @JsonProperty("points") Long points,
        @JsonProperty("remark") String remark
) {}
