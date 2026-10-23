package com.openforge.aimate.localbridge;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 本机 Cursor / Codex 等 worker 专用 API（Bearer lba_…）。
 */
@RestController
@RequestMapping("/api/local-bridge")
@RequiredArgsConstructor
public class LocalBridgeController {

    private final LocalBridgeService localBridgeService;

    @GetMapping("/me")
    public Map<String, Object> me() {
        return localBridgeService.me(requirePrincipal());
    }

    @PostMapping("/tasks/claim")
    public Map<String, Object> claim() {
        try {
            return localBridgeService.claimNext(requirePrincipal());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping("/tasks/{taskId}/context")
    public Map<String, Object> context(@PathVariable String taskId) {
        try {
            return localBridgeService.taskContext(requirePrincipal(), taskId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/tasks/{taskId}/complete")
    public Map<String, Object> complete(@PathVariable String taskId, @RequestBody CompleteBody body) {
        try {
            boolean failed = body != null && Boolean.TRUE.equals(body.failed());
            String result = body != null ? body.result() : null;
            String error = body != null ? body.error() : null;
            Long replyToId = body != null ? body.replyToId() : null;
            return localBridgeService.complete(requirePrincipal(), taskId, result, failed, error, replyToId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping("/soul/me")
    public Map<String, Object> soulMe() {
        return localBridgeService.soulProxy(requirePrincipal(), HttpMethod.GET, "/me", null);
    }

    @GetMapping("/soul/context")
    public Map<String, Object> soulContext(@RequestParam(value = "q", required = false) String q) {
        String path = "/context?q=" + UriUtils.encodeQueryParam(q == null ? "" : q, StandardCharsets.UTF_8);
        return localBridgeService.soulProxy(requirePrincipal(), HttpMethod.GET, path, null);
    }

    @GetMapping("/soul/memories")
    public Map<String, Object> soulMemories() {
        return localBridgeService.soulProxy(requirePrincipal(), HttpMethod.GET, "/memories", null);
    }

    @GetMapping("/soul/commitments")
    public Map<String, Object> soulCommitments() {
        return localBridgeService.soulProxy(requirePrincipal(), HttpMethod.GET, "/commitments", null);
    }

    @GetMapping("/soul/entities")
    public Map<String, Object> soulEntities() {
        return localBridgeService.soulProxy(requirePrincipal(), HttpMethod.GET, "/entities", null);
    }

    private LocalBridgePrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LocalBridgePrincipal p) {
            return p;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "需要本地桥接令牌 Authorization: Bearer lba_…");
    }

    public record CompleteBody(String result, Boolean failed, String error, Long replyToId) {}
}
