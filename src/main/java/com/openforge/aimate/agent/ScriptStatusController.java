package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.ContainerControlResult;
import com.openforge.aimate.agent.dto.DockerInstallInfoDto;
import com.openforge.aimate.agent.dto.ScriptEnvStatusDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
            log.warn("[ScriptStatus] Docker 未检测到（docker version 不可用或未安装），无法创建容器");
            return ResponseEntity.ok(new ScriptEnvStatusDto(
                    true, "none", null, idleMinutes,
                    "Docker 已配置但未检测到运行中的 Docker，请安装并启动 Docker 后刷新",
                    false, null, null, null, null));
        }
        String image = scriptDockerProperties.image();
        String memoryLimit = scriptDockerProperties.memoryLimit();
        Double cpuLimit = scriptDockerProperties.cpuLimit();
        if (userId == null) {
            log.warn("[ScriptStatus] userId=null -> 无法创建容器（未登录或 JWT 未注入 userId，请确认 /api/agent/script-status 带有效 Token）");
            return ResponseEntity.ok(ScriptEnvStatusDto.dockerNoContainer(idleMinutes, true, dockerVersion, image, memoryLimit, cpuLimit));
        }
        // 仅查询状态，不创建、不启动，避免「关闭容器后前端刷新状态」时误把容器拉起来
        var status = userContainerManager.getContainerStatusReadOnly(userId);
        if (status.state() == com.openforge.aimate.agent.UserContainerManager.ContainerState.RUNNING && status.containerName() != null) {
            log.debug("[ScriptStatus] userId={} container={} -> dockerRunning", userId, status.containerName());
            return ResponseEntity.ok(ScriptEnvStatusDto.dockerRunning(status.containerName(), idleMinutes, true, dockerVersion, image, memoryLimit, cpuLimit));
        }
        if (status.state() == com.openforge.aimate.agent.UserContainerManager.ContainerState.STOPPED) {
            String msg = idleMinutes <= 0
                    ? "容器已停止，可在系统设置中启动。"
                    : "容器已停止，可在系统设置中启动；空闲 %d 分钟后将自动回收。".formatted(idleMinutes);
            return ResponseEntity.ok(new ScriptEnvStatusDto(true, "none", status.containerName(), idleMinutes, msg, true, dockerVersion, image, memoryLimit, cpuLimit));
        }
        return ResponseEntity.ok(ScriptEnvStatusDto.dockerNoContainer(idleMinutes, true, dockerVersion, image, memoryLimit, cpuLimit));
    }

    /** 清除 Docker 检测缓存，下次 getScriptStatus 会重新执行 docker version */
    @PostMapping("/script-status/refresh")
    public ResponseEntity<Void> refreshScriptStatus() {
        dockerDetector.clearCache();
        return ResponseEntity.noContent().build();
    }

    /** 用户手动启动自己的容器（已存在则 start，不存在则创建） */
    @PostMapping("/container/start")
    public ResponseEntity<ContainerControlResult> startContainer() {
        Long userId = currentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ContainerControlResult.fail("未登录"));
        }
        if (!scriptDockerProperties.enabled()) {
            return ResponseEntity.badRequest().body(ContainerControlResult.fail("Docker 未启用"));
        }
        String name = userContainerManager.startUserContainer(userId);
        if (name != null) {
            return ResponseEntity.ok(ContainerControlResult.ok(name));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ContainerControlResult.fail("启动失败，请查看服务端日志或确认 Docker 已运行"));
    }

    /** 用户手动重启自己的容器（stop 后 start） */
    @PostMapping("/container/restart")
    public ResponseEntity<ContainerControlResult> restartContainer() {
        Long userId = currentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ContainerControlResult.fail("未登录"));
        }
        if (!scriptDockerProperties.enabled()) {
            return ResponseEntity.badRequest().body(ContainerControlResult.fail("Docker 未启用"));
        }
        String name = userContainerManager.restartUserContainer(userId);
        if (name != null) {
            return ResponseEntity.ok(ContainerControlResult.ok(name));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ContainerControlResult.fail("重启失败，请查看服务端日志或确认 Docker 已运行"));
    }

    /** 用户手动关闭自己的容器（仅 stop，不删除） */
    @PostMapping("/container/stop")
    public ResponseEntity<ContainerControlResult> stopContainer() {
        Long userId = currentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ContainerControlResult.fail("未登录"));
        }
        if (!scriptDockerProperties.enabled()) {
            return ResponseEntity.badRequest().body(ContainerControlResult.fail("Docker 未启用"));
        }
        boolean ok = userContainerManager.stopUserContainer(userId);
        if (ok) {
            return ResponseEntity.ok(new ContainerControlResult(true, "已停止", null));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ContainerControlResult.fail("停止失败，请查看服务端日志"));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        return null;
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
