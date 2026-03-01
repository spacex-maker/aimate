package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.DockerInstallInfoDto;
import com.openforge.aimate.agent.dto.ScriptEnvStatusDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Script execution environment status (Docker on/off, container running, Docker 检测与安装说明).
 * GET /api/agent/script-status — 自动查询 Docker 环境并返回状态
 * POST /api/agent/script-status/refresh — 清除检测缓存并重新检测
 * GET /api/agent/docker-install-info — 返回当前系统安装 Docker 的说明与链接
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class ScriptStatusController {

    private final UserContainerManager userContainerManager;
    private final ScriptDockerProperties scriptDockerProperties;
    private final DockerDetector dockerDetector;

    @GetMapping("/script-status")
    public ResponseEntity<ScriptEnvStatusDto> getScriptStatus() {
        Optional<String> versionOpt = dockerDetector.getDockerVersion();
        Boolean dockerAvailable = versionOpt.isPresent();
        String dockerVersion = versionOpt.orElse(null);

        Long userId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) userId = id;

        if (!scriptDockerProperties.enabled()) {
            log.debug("[ScriptStatus] dockerEnabled=false, returning hostOnly");
            return ResponseEntity.ok(ScriptEnvStatusDto.hostOnly(
                    "脚本在本机执行（Docker 未启用）", dockerAvailable, dockerVersion));
        }
        int idleMinutes = scriptDockerProperties.idleMinutes();
        if (!dockerAvailable) {
            return ResponseEntity.ok(new ScriptEnvStatusDto(
                    true, "none", null, idleMinutes,
                    "Docker 已配置但未检测到运行中的 Docker，请安装并启动 Docker 后刷新",
                    false, null, null, null, null));
        }
        String image = scriptDockerProperties.image();
        String memoryLimit = scriptDockerProperties.memoryLimit();
        Double cpuLimit = scriptDockerProperties.cpuLimit();
        if (userId == null) {
            return ResponseEntity.ok(ScriptEnvStatusDto.dockerNoContainer(idleMinutes, true, dockerVersion, image, memoryLimit, cpuLimit));
        }
        // 用户进入会话时：若已启用 Docker 则确保该用户有容器（已创建则复用，未创建则创建）
        String containerName = userContainerManager.getOrCreateContainer(userId);
        if (containerName != null) {
            log.debug("[ScriptStatus] userId={} container={} -> dockerRunning", userId, containerName);
            return ResponseEntity.ok(ScriptEnvStatusDto.dockerRunning(containerName, idleMinutes, true, dockerVersion, image, memoryLimit, cpuLimit));
        }
        log.debug("[ScriptStatus] userId={} getOrCreateContainer returned null -> dockerNoContainer", userId);
        return ResponseEntity.ok(ScriptEnvStatusDto.dockerNoContainer(idleMinutes, true, dockerVersion, image, memoryLimit, cpuLimit));
    }

    /** 清除 Docker 检测缓存，下次 getScriptStatus 会重新执行 docker version */
    @PostMapping("/script-status/refresh")
    public ResponseEntity<Void> refreshScriptStatus() {
        dockerDetector.clearCache();
        return ResponseEntity.noContent().build();
    }

    /** 根据当前服务器操作系统返回 Docker 安装说明与链接，供前端展示 */
    @GetMapping("/docker-install-info")
    public ResponseEntity<DockerInstallInfoDto> getDockerInstallInfo() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        DockerInstallInfoDto dto;
        if (osName.contains("linux")) {
            dto = DockerInstallInfoDto.linux("curl -fsSL https://get.docker.com | sh");
        } else if (osName.contains("windows")) {
            dto = DockerInstallInfoDto.windows();
        } else if (osName.contains("mac")) {
            dto = DockerInstallInfoDto.mac();
        } else {
            dto = DockerInstallInfoDto.unknown();
        }
        return ResponseEntity.ok(dto);
    }
}
