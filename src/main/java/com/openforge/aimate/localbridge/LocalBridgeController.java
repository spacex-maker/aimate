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

    /** 本机连接器心跳：刷新在线状态与设备信息。 */
    @PostMapping("/heartbeat")
    public Map<String, Object> heartbeat(@RequestBody(required = false) Map<String, Object> body) {
        try {
            return localBridgeService.heartbeat(requirePrincipal(), body);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /**
     * @param waitSeconds 可选长轮询秒数 0–30；有任务立即返回，无任务挂起至超时。
     *                    替代高频短轮询，仍兼容 curl/bat，无需 WebSocket。
     */
    @PostMapping("/tasks/claim")
    public Map<String, Object> claim(
            @RequestParam(value = "waitSeconds", required = false, defaultValue = "0") int waitSeconds,
            @RequestParam(value = "deviceKey", required = false) String deviceKey
    ) {
        try {
            return localBridgeService.claimNext(requirePrincipal(), waitSeconds, deviceKey);
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

    /** 续租：等待本机应用跑完时由 Connector 周期性调用，避免 CLAIMED 被回收。 */
    @PostMapping("/tasks/{taskId}/lease")
    public Map<String, Object> renewLease(
            @PathVariable String taskId,
            @RequestParam(value = "deviceKey", required = false) String deviceKey
    ) {
        try {
            return localBridgeService.renewLease(requirePrincipal(), taskId, deviceKey);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** 执行中进度（不 complete）。stream/content 时原地更新同一条群消息。 */
    @PostMapping("/tasks/{taskId}/progress")
    public Map<String, Object> progress(
            @PathVariable String taskId,
            @RequestParam(value = "deviceKey", required = false) String deviceKey,
            @RequestBody(required = false) ProgressBody body
    ) {
        try {
            String msg = body != null ? body.message() : null;
            boolean stream = body != null && Boolean.TRUE.equals(body.stream());
            String content = body != null ? body.content() : null;
            return localBridgeService.reportProgress(
                    requirePrincipal(), taskId, msg, deviceKey, stream, content);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /** 流式增量回传（累积正文）；首包建消息，后续 SSE 原地更新。 */
    @PostMapping("/tasks/{taskId}/stream")
    public Map<String, Object> stream(
            @PathVariable String taskId,
            @RequestParam(value = "deviceKey", required = false) String deviceKey,
            @RequestBody(required = false) StreamBody body
    ) {
        try {
            String content = body != null ? body.content() : null;
            if (!StringUtils.hasText(content) && body != null && StringUtils.hasText(body.delta())) {
                // 仅 delta 时由服务端无法拼历史，要求客户端传累积 content；兼容：当 delta 当 content
                content = body.delta();
            }
            String kind = body != null ? body.kind() : null;
            return localBridgeService.reportStream(requirePrincipal(), taskId, content, deviceKey, kind);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/tasks/{taskId}/complete")
    public Map<String, Object> complete(@PathVariable String taskId, @RequestBody CompleteBody body) {
        try {
            boolean failed = body != null && Boolean.TRUE.equals(body.failed());
            boolean skipped = body != null && Boolean.TRUE.equals(body.skipped());
            String result = body != null ? body.result() : null;
            String error = body != null ? body.error() : null;
            Long replyToId = body != null ? body.replyToId() : null;
            return localBridgeService.complete(
                    requirePrincipal(), taskId, result, failed, error, replyToId, skipped);
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

    @PostMapping("/soul/memories")
    public Map<String, Object> soulMemoriesPost(@RequestBody(required = false) Map<String, Object> body) {
        return localBridgeService.soulProxy(requirePrincipal(), HttpMethod.POST, "/memories", body);
    }

    @GetMapping("/soul/commitments")
    public Map<String, Object> soulCommitments() {
        return localBridgeService.soulProxy(requirePrincipal(), HttpMethod.GET, "/commitments", null);
    }

    @PostMapping("/soul/commitments")
    public Map<String, Object> soulCommitmentsPost(@RequestBody(required = false) Map<String, Object> body) {
        return localBridgeService.soulProxy(requirePrincipal(), HttpMethod.POST, "/commitments", body);
    }

    @GetMapping("/soul/entities")
    public Map<String, Object> soulEntities() {
        return localBridgeService.soulProxy(requirePrincipal(), HttpMethod.GET, "/entities", null);
    }

    @PostMapping("/soul/entities")
    public Map<String, Object> soulEntitiesPost(@RequestBody(required = false) Map<String, Object> body) {
        return localBridgeService.soulProxy(requirePrincipal(), HttpMethod.POST, "/entities", body);
    }

    private LocalBridgePrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LocalBridgePrincipal p) {
            return p;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "需要本地桥接令牌 Authorization: Bearer lba_…");
    }

    public record CompleteBody(
            String result, Boolean failed, String error, Long replyToId, Boolean skipped
    ) {}

    public record ProgressBody(String message, Boolean stream, String content) {}

    /**
     * content=累积全文；delta=增量；kind=thinking|progress
     * thinking=Cursor 真思考；progress=本机执行/.partial（默认）
     */
    public record StreamBody(String content, String delta, Integer seq, String kind) {}
}
