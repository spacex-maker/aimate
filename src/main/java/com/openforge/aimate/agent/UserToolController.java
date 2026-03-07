package com.openforge.aimate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.agent.dto.CreateUserToolRequest;
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
import java.util.Set;

/**
 * 用户查看「系统工具 + 自有工具」、编辑/删除自有工具。
 * 路径 /api/users/{userId}/tools，仅允许操作当前登录用户（path userId 需等于当前用户）。
 */
@RestController
@RequestMapping("/api/users/{userId}/tools")
@RequiredArgsConstructor
public class UserToolController {

    private static final Set<String> RESERVED_TOOL_NAMES = Set.of(
            "recall_memory", "store_memory", "tavily_search", "create_tool",
            "install_container_package", "run_container_cmd", "write_container_file"
    );

    private final AgentToolRepository toolRepository;
    private final ObjectMapper objectMapper;
    private final ToolIndexService toolIndexService;

    @PostMapping
    public ResponseEntity<UserToolDto> create(@PathVariable Long userId, @RequestBody CreateUserToolRequest body) {
        ensureCurrentUser(userId);
        String err = validateCreateRequest(body);
        if (err != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, err);
        }
        String toolName = body.toolName().trim();
        if (toolRepository.findByToolNameAndUserId(toolName, userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "您已有同名工具，请换一个名称或先编辑/删除旧工具");
        }
        if (toolRepository.findByToolNameAndUserIdIsNull(toolName).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工具名与系统工具重名，请换一个名称");
        }
        AgentTool.ToolType toolType = AgentTool.ToolType.valueOf(body.toolType().trim().toUpperCase());
        if (toolType == AgentTool.ToolType.JAVA_NATIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 PYTHON_SCRIPT、NODE_SCRIPT、SHELL_CMD，不可创建 JAVA_NATIVE");
        }
        String scriptContent = body.scriptContent() != null ? body.scriptContent().trim() : "";
        String entryPoint = body.entryPoint() != null && !body.entryPoint().isBlank()
                ? body.entryPoint().trim()
                : toolName + extensionFor(toolType);
        AgentTool entity = AgentTool.builder()
                .toolName(toolName)
                .toolDescription(body.toolDescription().trim())
                .inputSchema(body.inputSchema().trim())
                .toolType(toolType)
                .scriptContent(scriptContent.isEmpty() ? null : scriptContent)
                .entryPoint(entryPoint)
                .isActive(true)
                .userId(userId)
                .build();
        entity = toolRepository.save(entity);
        toolIndexService.indexToolByName(toolName, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserToolDto.from(entity));
    }

    private static String extensionFor(AgentTool.ToolType type) {
        return switch (type) {
            case PYTHON_SCRIPT -> ".py";
            case NODE_SCRIPT -> ".js";
            case SHELL_CMD -> ".sh";
            default -> "";
        };
    }

    private String validateCreateRequest(CreateUserToolRequest body) {
        if (body.toolName() == null || body.toolName().isBlank()) {
            return "工具名不能为空";
        }
        String name = body.toolName().trim();
        if (!name.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
            return "工具名须以字母开头，仅含字母、数字、下划线（如 get_weather、calc_sum）";
        }
        if (RESERVED_TOOL_NAMES.contains(name)) {
            return "工具名不能与内置工具重名: " + name;
        }
        if (body.toolDescription() == null || body.toolDescription().isBlank()) {
            return "工具描述不能为空";
        }
        if (body.inputSchema() == null || body.inputSchema().isBlank()) {
            return "参数结构（input_schema）不能为空，须为合法 JSON 对象";
        }
        try {
            JsonNode node = objectMapper.readTree(body.inputSchema());
            if (!node.isObject()) return "input_schema 须为 JSON 对象";
        } catch (Exception e) {
            return "input_schema 须为合法 JSON: " + e.getMessage();
        }
        if (body.toolType() == null || body.toolType().isBlank()) {
            return "工具类型不能为空";
        }
        String typeStr = body.toolType().trim().toUpperCase();
        if (!Set.of("PYTHON_SCRIPT", "NODE_SCRIPT", "SHELL_CMD").contains(typeStr)) {
            return "工具类型须为 PYTHON_SCRIPT、NODE_SCRIPT 或 SHELL_CMD 之一";
        }
        return null;
    }

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
