package com.openforge.aimate.localbridge;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 登录用户：为本地 AI Agent 生成「接入提示词」与专属令牌。
 */
@RestController
@RequestMapping("/api/me/agents")
@RequiredArgsConstructor
public class LocalBridgeOwnerController {

    private final LocalBridgeService localBridgeService;

    @PostMapping("/{id}/local-bridge/connect")
    public Map<String, Object> connect(@PathVariable Long id, @RequestBody(required = false) ConnectBody body) {
        try {
            boolean rotate = body != null && Boolean.TRUE.equals(body.rotate());
            return localBridgeService.issueConnectBundle(requireUserId(), id, rotate);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private Long requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    }

    public record ConnectBody(Boolean rotate) {}
}
