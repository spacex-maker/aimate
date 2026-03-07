package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 用户更新自有工具的请求体（均可选）。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateUserToolRequest(
        String toolDescription,
        String inputSchema,
        String scriptContent,
        String entryPoint,
        Boolean isActive
) {}
