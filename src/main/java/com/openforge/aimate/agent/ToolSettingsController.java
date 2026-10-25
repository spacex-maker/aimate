package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.SessionResponse;
import com.openforge.aimate.agent.dto.ToolSettingsDto;
import com.openforge.aimate.domain.UserToolSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 系统工具开关。
 * <ul>
 *   <li>带 sessionId / userAgentId：单聊生效值（会话覆盖优先，否则 Agent 默认）</li>
 *   <li>都不带：用户级（管理兼容 / 旧客户端）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/agent/tool-settings")
@RequiredArgsConstructor
public class ToolSettingsController {

    private final UserToolSettingsService toolSettingsService;
    private final UserAgentService userAgentService;

    @GetMapping
    public ResponseEntity<ToolSettingsDto> get(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Long userAgentId
    ) {
        Long userId = getCurrentUserId();
        if (sessionId != null || userAgentId != null) {
            return ResponseEntity.ok(toolSettingsService.resolveForChat(userId, sessionId, userAgentId));
        }
        UserToolSettings s = toolSettingsService.getOrCreate(userId);
        return ResponseEntity.ok(ToolSettingsDto.from(s));
    }

    @PutMapping
    public ResponseEntity<ToolSettingsDto> update(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Long userAgentId,
            @RequestBody ToolSettingsDto body
    ) {
        Long userId = getCurrentUserId();
        if (sessionId != null || userAgentId != null) {
            try {
                String sid = sessionId;
                // 首页尚无 session：先打开该伙伴单聊再写覆盖
                if (!StringUtils.hasText(sid) && userAgentId != null && userId != null) {
                    SessionResponse opened = userAgentService.openChat(userId, userAgentId);
                    sid = opened.sessionId();
                }
                return ResponseEntity.ok(
                        toolSettingsService.updateChatOverride(userId, sid, userAgentId, body));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            } catch (IllegalStateException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
            }
        }
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
