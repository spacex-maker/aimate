package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.ToolSettingsDto;
import com.openforge.aimate.domain.UserToolSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 用户系统工具开关：长期记忆、联网搜索、AI 自主编写工具、用户系统脚本执行。
 */
@RestController
@RequestMapping("/api/agent/tool-settings")
@RequiredArgsConstructor
public class ToolSettingsController {

    private final UserToolSettingsService toolSettingsService;

    @GetMapping
    public ResponseEntity<ToolSettingsDto> get() {
        Long userId = getCurrentUserId();
        UserToolSettings s = toolSettingsService.getOrCreate(userId);
        return ResponseEntity.ok(ToolSettingsDto.from(s));
    }

    @PutMapping
    public ResponseEntity<ToolSettingsDto> update(@RequestBody ToolSettingsDto body) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(body);
        }
        UserToolSettings s = toolSettingsService.update(
                userId,
                body.memoryEnabled(),
                body.webSearchEnabled(),
                body.createToolEnabled(),
                body.scriptExecEnabled()
        );
        return ResponseEntity.ok(ToolSettingsDto.from(s));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) {
            return id;
        }
        return null;
    }
}
