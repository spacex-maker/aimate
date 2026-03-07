package com.openforge.aimate.agent.dto;

import com.openforge.aimate.domain.AgentTool;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 工具列表项：系统工具 + 用户工具，供前端展示。isSystem 为 true 时用户不可编辑/删除。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserToolDto(
        Long id,
        String toolName,
        String toolDescription,
        String toolType,
        Boolean isActive,
        /** true 表示系统工具，用户不可修改/删除 */
        boolean isSystem,
        Long userId,
        String inputSchema,
        String scriptContent,
        String entryPoint
) {
    public static UserToolDto from(AgentTool t) {
        return new UserToolDto(
                t.getId(),
                t.getToolName(),
                t.getToolDescription(),
                t.getToolType() != null ? t.getToolType().name() : null,
                t.getIsActive(),
                t.getUserId() == null,
                t.getUserId(),
                t.getInputSchema(),
                t.getScriptContent(),
                t.getEntryPoint()
        );
    }
}
