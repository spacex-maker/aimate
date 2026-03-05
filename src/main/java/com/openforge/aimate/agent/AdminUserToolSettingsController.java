package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.ToolSettingsDto;
import com.openforge.aimate.domain.UserToolSettings;
import com.openforge.aimate.agent.UserToolSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员为指定用户配置系统工具开关。
 * 路径受 SecurityConfig 中 /api/admin/** + ADMIN 权限保护。
 */
@RestController
@RequestMapping("/api/admin/users/{userId}/tool-settings")
@RequiredArgsConstructor
public class AdminUserToolSettingsController {

    private final UserToolSettingsService toolSettingsService;

    @GetMapping
    public ResponseEntity<ToolSettingsDto> get(@PathVariable Long userId) {
        UserToolSettings s = toolSettingsService.getOrCreate(userId);
        return ResponseEntity.ok(ToolSettingsDto.from(s));
    }

    @PutMapping
    public ResponseEntity<ToolSettingsDto> update(
            @PathVariable Long userId,
            @RequestBody ToolSettingsDto body
    ) {
        UserToolSettings s = toolSettingsService.update(
                userId,
                body.memoryEnabled(),
                body.webSearchEnabled(),
                body.createToolEnabled(),
                body.scriptExecEnabled()
        );
        return ResponseEntity.ok(ToolSettingsDto.from(s));
    }
}

