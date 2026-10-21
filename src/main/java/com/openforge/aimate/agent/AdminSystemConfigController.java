package com.openforge.aimate.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openforge.aimate.agent.dto.SystemConfigItemDto;
import com.openforge.aimate.agent.dto.UpdateSystemConfigRequest;
import com.openforge.aimate.domain.SystemConfig;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.repository.SystemConfigRepository;
import com.openforge.aimate.repository.SystemModelRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 管理员系统配置管理：查看全部配置项、更新配置值与描述。
 * 路径受 SecurityConfig 中 /api/admin/** + ADMIN 权限保护。
 */
@RestController
@RequestMapping("/api/admin/system-configs")
public class AdminSystemConfigController {

    private static final String DEEPSEEK_KEY_PREFIX = "DEEPSEEK_API_KEY";

    private final SystemConfigRepository systemConfigRepository;
    private final SystemModelRepository systemModelRepository;

    public AdminSystemConfigController(
            SystemConfigRepository systemConfigRepository,
            SystemModelRepository systemModelRepository
    ) {
        this.systemConfigRepository = systemConfigRepository;
        this.systemModelRepository = systemModelRepository;
    }

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

    /** 新增一条 DeepSeek API Key。configKey 可省略，由服务端按 DEEPSEEK_API_KEY[_n] 分配。 */
    @PostMapping
    public ResponseEntity<SystemConfigItemDto> create(@RequestBody CreateDeepseekKeyRequest request) {
        if (request == null || !StringUtils.hasText(request.configValue())) {
            throw new ResponseStatusException(BAD_REQUEST, "请填写 API Key");
        }
        String configKey = StringUtils.hasText(request.configKey())
                ? request.configKey().trim()
                : nextDeepseekKey();
        if (!isDeepseekKey(configKey)) {
            throw new ResponseStatusException(BAD_REQUEST, "DeepSeek Key 的配置键须为 DEEPSEEK_API_KEY 或 DEEPSEEK_API_KEY_后缀");
        }
        if (systemConfigRepository.findByConfigKey(configKey).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "配置键已存在: " + configKey);
        }
        SystemConfig config = new SystemConfig();
        config.setConfigKey(configKey);
        config.setConfigValue(request.configValue().trim());
        config.setDescription(StringUtils.hasText(request.description())
                ? request.description().trim()
                : configKey);
        config = systemConfigRepository.save(config);
        return ResponseEntity.ok(SystemConfigItemDto.from(config));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        SystemConfig config = systemConfigRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "系统配置不存在: " + id));
        if (!isDeepseekKey(config.getConfigKey())) {
            throw new ResponseStatusException(BAD_REQUEST, "只能删除 DeepSeek API Key");
        }
        boolean used = systemModelRepository.findAll().stream()
                .map(SystemModel::getApiKeyConfigKey)
                .anyMatch(key -> config.getConfigKey().equals(key));
        if (used) {
            throw new ResponseStatusException(CONFLICT, "仍有模型使用该 Key，请先在模型管理中改选");
        }
        systemConfigRepository.delete(config);
        return ResponseEntity.noContent().build();
    }

    private String nextDeepseekKey() {
        var existing = systemConfigRepository.findAllByOrderByConfigKeyAsc().stream()
                .map(SystemConfig::getConfigKey)
                .filter(AdminSystemConfigController::isDeepseekKey)
                .collect(java.util.stream.Collectors.toSet());
        if (!existing.contains(DEEPSEEK_KEY_PREFIX)) {
            return DEEPSEEK_KEY_PREFIX;
        }
        for (int i = 2; i < 100; i++) {
            String candidate = DEEPSEEK_KEY_PREFIX + "_" + i;
            if (!existing.contains(candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(BAD_REQUEST, "DeepSeek API Key 数量已达上限");
    }

    static boolean isDeepseekKey(String configKey) {
        return configKey != null && configKey.matches("DEEPSEEK_API_KEY(_[A-Za-z0-9]{1,40})?");
    }

    public record CreateDeepseekKeyRequest(
            @JsonProperty("configKey") String configKey,
            @JsonProperty("configValue") String configValue,
            @JsonProperty("description") String description
    ) {}
}

