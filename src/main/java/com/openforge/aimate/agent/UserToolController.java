package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.UpdateUserToolRequest;
import com.openforge.aimate.agent.dto.UserToolDto;
import com.openforge.aimate.domain.AgentTool;
import com.openforge.aimate.repository.AgentToolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户查看「系统工具 + 自有工具」、编辑/删除自有工具。
 * 路径 /api/users/{userId}/tools，仅允许操作当前登录用户（path userId 需等于当前用户）。
 */
@RestController
@RequestMapping("/api/users/{userId}/tools")
@RequiredArgsConstructor
public class UserToolController {

    private final AgentToolRepository toolRepository;

    @GetMapping
    public ResponseEntity<List<UserToolDto>> list(@PathVariable Long userId) {
        ensureCurrentUser(userId);
        List<AgentTool> systemTools = toolRepository.findByUserIdIsNullAndIsActiveTrue();
        List<AgentTool> userTools = toolRepository.findByUserIdOrderByIdAsc(userId);
        List<UserToolDto> systemDtos = systemTools.stream().map(UserToolDto::from).toList();
        List<UserToolDto> userDtos = userTools.stream().map(UserToolDto::from).toList();
        List<UserToolDto> all = new ArrayList<>(systemDtos);
        all.addAll(userDtos);
        return ResponseEntity.ok(all);
    }

    @PutMapping("/{toolId}")
    public ResponseEntity<UserToolDto> update(
            @PathVariable Long userId,
            @PathVariable Long toolId,
            @RequestBody UpdateUserToolRequest body
    ) {
        ensureCurrentUser(userId);
        AgentTool tool = toolRepository.findById(toolId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "工具不存在"));
        if (tool.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "系统工具不可修改");
        }
        if (!tool.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅可修改自己的工具");
        }
        if (body.toolDescription() != null) tool.setToolDescription(body.toolDescription());
        if (body.inputSchema() != null) tool.setInputSchema(body.inputSchema());
        if (body.scriptContent() != null) tool.setScriptContent(body.scriptContent());
        if (body.entryPoint() != null) tool.setEntryPoint(body.entryPoint());
        if (body.isActive() != null) tool.setIsActive(body.isActive());
        toolRepository.save(tool);
        return ResponseEntity.ok(UserToolDto.from(tool));
    }

    @DeleteMapping("/{toolId}")
    public ResponseEntity<Void> delete(@PathVariable Long userId, @PathVariable Long toolId) {
        ensureCurrentUser(userId);
        AgentTool tool = toolRepository.findById(toolId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "工具不存在"));
        if (tool.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "系统工具不可删除");
        }
        if (!tool.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅可删除自己的工具");
        }
        toolRepository.delete(tool);
        return ResponseEntity.noContent().build();
    }

    private void ensureCurrentUser(Long pathUserId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long currentId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (!currentId.equals(pathUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅可操作自己的工具");
        }
    }
}
