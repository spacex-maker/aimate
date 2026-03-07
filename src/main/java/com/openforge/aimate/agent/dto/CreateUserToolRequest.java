package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 用户新增工具的请求体，字段规范与 AI 生成工具一致。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateUserToolRequest(
        String toolName,
        String toolDescription,
        String inputSchema,
        String toolType,
        String scriptContent,
        String entryPoint
) {}
