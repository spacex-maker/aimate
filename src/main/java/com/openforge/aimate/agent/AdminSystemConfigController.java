package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.SystemConfigItemDto;
import com.openforge.aimate.agent.dto.UpdateSystemConfigRequest;
import com.openforge.aimate.domain.SystemConfig;
import com.openforge.aimate.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 管理员系统配置管理：查看全部配置项、更新配置值与描述。
 * 路径受 SecurityConfig 中 /api/admin/** + ADMIN 权限保护。
 */
@RestController
@RequestMapping("/api/admin/system-configs")
@RequiredArgsConstructor
public class AdminSystemConfigController {

    private final SystemConfigRepository systemConfigRepository;

    @GetMapping
    public ResponseEntity<List<SystemConfigItemDto>> listAll() {
        List<SystemConfigItemDto> list = systemConfigRepository.findAllByOrderByConfigKeyAsc()
                .stream()
                .map(SystemConfigItemDto::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SystemConfigItemDto> update(
            @PathVariable Long id,
            @RequestBody UpdateSystemConfigRequest request
    ) {
        SystemConfig config = systemConfigRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "系统配置不存在: " + id));

        if (request.configValue() != null) {
            config.setConfigValue(request.configValue());
        }
        if (request.description() != null) {
            config.setDescription(request.description());
        }

        config = systemConfigRepository.save(config);
        return ResponseEntity.ok(SystemConfigItemDto.from(config));
    }
}

