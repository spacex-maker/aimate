package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.UserContainerStatusDto;
import com.openforge.aimate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员查看所有用户脚本容器状态的接口。
 * 路径受 SecurityConfig 中 /api/admin/** + ADMIN 权限保护。
 */
@RestController
@RequestMapping("/api/admin/containers")
@RequiredArgsConstructor
public class AdminContainerController {

    private final UserContainerManager userContainerManager;
    private final UserRepository       userRepository;

    @GetMapping
    public ResponseEntity<List<UserContainerStatusDto>> listAll() {
        var summaries = userContainerManager.listAllUserContainersWithStats();
        if (summaries.isEmpty()) return ResponseEntity.ok(List.of());

        // 批量查用户名，避免逐条查询
        var userIds = summaries.stream().map(UserContainerManager.ContainerSummary::userId).collect(Collectors.toSet());
        Map<Long, String> idToName = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getUsername()));

        List<UserContainerStatusDto> dto = summaries.stream()
                .map(s -> new UserContainerStatusDto(
                        s.userId(),
                        idToName.getOrDefault(s.userId(), "unknown"),
                        s.containerName(),
                        s.status(),
                        s.lastUsedAt(),
                        s.cpuPercent(),
                        s.memUsage(),
                        s.memPercent()
                ))
                .toList();
        return ResponseEntity.ok(dto);
    }
}
