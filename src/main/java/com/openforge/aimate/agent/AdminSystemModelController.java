package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.SystemModelDto;
import com.openforge.aimate.agent.dto.UpdateSystemModelRequest;
import com.openforge.aimate.config.SystemConfigService;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.repository.SystemModelRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 管理员系统模型管理：查看全部（含已关闭）、开启/关闭模型。
 * 路径受 SecurityConfig 中 /api/admin/** + ADMIN 权限保护。
 */
@RestController
@RequestMapping("/api/admin/system-models")
public class AdminSystemModelController {

    private final SystemModelRepository systemModelRepository;
    private final SystemConfigService systemConfigService;

    public AdminSystemModelController(
            SystemModelRepository systemModelRepository,
            SystemConfigService systemConfigService
    ) {
        this.systemModelRepository = systemModelRepository;
        this.systemConfigService = systemConfigService;
    }

    @GetMapping
    public ResponseEntity<List<SystemModelDto>> listAll() {
        List<SystemModelDto> list = systemModelRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(SystemModelDto::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SystemModelDto> update(
            @PathVariable Long id,
            @RequestBody UpdateSystemModelRequest request) {
        SystemModel model = systemModelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "系统模型不存在: " + id));

        if (request.enabled() != null) {
            model.setEnabled(request.enabled());
        }
        if (request.sortOrder() != null) {
            model.setSortOrder(request.sortOrder());
        }
        if (request.apiKeyConfigKey() != null) {
            String key = request.apiKeyConfigKey().trim();
            if (key.isEmpty()) {
                model.setApiKeyConfigKey(null);
            } else {
                if (!systemConfigService.exists(key)) {
                    throw new ResponseStatusException(NOT_FOUND, "API Key 配置不存在: " + key);
                }
                model.setApiKeyConfigKey(key);
            }
        }
        if (request.tip() != null) {
            String tip = request.tip().trim();
            model.setTip(tip.isEmpty() ? null : tip.substring(0, Math.min(tip.length(), 128)));
        }
        if (request.contextWindowTokens() != null) {
            model.setContextWindowTokens(Math.max(1_000, request.contextWindowTokens()));
        }
        if (request.pointsPer1kPromptTokens() != null) {
            model.setPointsPer1kPromptTokens(Math.max(0, request.pointsPer1kPromptTokens()));
        }
        if (request.pointsPer1kCompletionTokens() != null) {
            model.setPointsPer1kCompletionTokens(Math.max(0, request.pointsPer1kCompletionTokens()));
        }
        if (request.costInputCnyPer1mPeak() != null) {
            model.setCostInputCnyPer1mPeak(request.costInputCnyPer1mPeak());
        }
        if (request.costInputCnyPer1mOffpeak() != null) {
            model.setCostInputCnyPer1mOffpeak(request.costInputCnyPer1mOffpeak());
        }
        if (request.costOutputCnyPer1mPeak() != null) {
            model.setCostOutputCnyPer1mPeak(request.costOutputCnyPer1mPeak());
        }
        if (request.costOutputCnyPer1mOffpeak() != null) {
            model.setCostOutputCnyPer1mOffpeak(request.costOutputCnyPer1mOffpeak());
        }

        model = systemModelRepository.save(model);
        return ResponseEntity.ok(SystemModelDto.from(model));
    }
}
