package com.openforge.aimate.localbridge;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 登录用户：本地 AI 接入提示词、设备登记。
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

    /** 添加设备：CLIENT=客户端指引；PROMPT=登记并返回提示词 */
    @PostMapping("/{id}/local-devices")
    public Map<String, Object> registerDevice(@PathVariable Long id, @RequestBody RegisterDeviceBody body) {
        try {
            String mode = body != null ? body.linkMode() : null;
            String label = body != null ? body.label() : null;
            return localBridgeService.registerDevice(requireUserId(), id, mode, label);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @DeleteMapping("/{id}/local-devices")
    public Map<String, Object> removeDevice(
            @PathVariable Long id,
            @RequestParam("deviceKey") String deviceKey
    ) {
        try {
            return localBridgeService.removeDevice(requireUserId(), id, deviceKey);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /** 手动关闭连接（离线，心跳不再复活） */
    @PostMapping("/{id}/local-devices/disconnect")
    public Map<String, Object> disconnectDevice(
            @PathVariable Long id,
            @RequestBody DeviceKeyBody body
    ) {
        try {
            String key = body != null ? body.deviceKey() : null;
            return localBridgeService.disconnectDevice(requireUserId(), id, key);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /** 允许已关闭的设备再次上线 */
    @PostMapping("/{id}/local-devices/allow-reconnect")
    public Map<String, Object> allowReconnect(
            @PathVariable Long id,
            @RequestBody DeviceKeyBody body
    ) {
        try {
            String key = body != null ? body.deviceKey() : null;
            return localBridgeService.allowDeviceReconnect(requireUserId(), id, key);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private Long requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long uid) return uid;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    }

    public record ConnectBody(Boolean rotate) {}

    public record RegisterDeviceBody(String linkMode, String label) {}

    public record DeviceKeyBody(String deviceKey) {}
}
