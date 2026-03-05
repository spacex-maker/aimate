package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.HostResourceStatusDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员查看宿主机与 Docker 资源配置摘要，用于容器监控页顶部展示。
 * 路径受 SecurityConfig 中 /api/admin/** + ADMIN 权限保护。
 */
@RestController
@RequestMapping("/api/admin/host-status")
@RequiredArgsConstructor
public class AdminHostStatusController {

    private final HostStatusService hostStatusService;

    @GetMapping
    public ResponseEntity<HostResourceStatusDto> getHostStatus() {
        return ResponseEntity.ok(hostStatusService.snapshot());
    }
}

