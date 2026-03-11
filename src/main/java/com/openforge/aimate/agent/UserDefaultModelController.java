package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.AvailableModelsDto;
import com.openforge.aimate.agent.dto.SystemModelDto;
import com.openforge.aimate.agent.dto.UpdateUserDefaultModelRequest;
import com.openforge.aimate.agent.dto.UserDefaultModelDto;
import com.openforge.aimate.agent.dto.UserModelDto;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.domain.UserApiKey;
import com.openforge.aimate.domain.UserDefaultModel;
import com.openforge.aimate.repository.SystemModelRepository;
import com.openforge.aimate.repository.UserApiKeyRepository;
import com.openforge.aimate.repository.UserDefaultModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * 记录用户在聊天输入框中最近一次选择的模型（系统模型 / 用户自有模型）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/user-default-model")
public class UserDefaultModelController {

    private final UserDefaultModelRepository userDefaultModelRepository;
    private final SystemModelRepository      systemModelRepository;
    private final UserApiKeyRepository       userApiKeyRepository;

    /**
     * 查询当前登录用户可用的所有模型信息：
     * - 用户自有模型（user_api_keys, key_type=LLM）；
     * - 系统模型（system_models, enabled=true, sort_order 升序）；
     * - 用户首选模型（user_default_models），用于前端默认选中。
     */
    @GetMapping
    public ResponseEntity<AvailableModelsDto> get() {
        Long userId = currentUserId();
        var systemModels = systemModelRepository.findByEnabledTrueOrderBySortOrderAsc()
                .stream()
                .map(SystemModelDto::from)
                .toList();

        UserDefaultModelDto defaultModel;
        java.util.List<UserModelDto> userModels;

        if (userId == null) {
            // 未登录时：无用户模型，无首选模型，系统模型列表照常返回
            userModels = java.util.List.of();
            defaultModel = new UserDefaultModelDto(null, null, null);
        } else {
            userModels = userApiKeyRepository.findByUserIdAndKeyTypeAndIsActiveTrue(
                            userId, com.openforge.aimate.domain.UserApiKey.KeyType.LLM)
                    .stream()
                    .map(UserModelDto::from)
                    .toList();
            defaultModel = userDefaultModelRepository.findByUserId(userId)
                    .map(UserDefaultModelDto::from)
                    .orElseGet(() -> new UserDefaultModelDto(null, null, null));
        }

        return ResponseEntity.ok(new AvailableModelsDto(userModels, systemModels, defaultModel));
    }

    /** 更新当前登录用户的首选模型（在聊天输入框切换模型时调用）。 */
    @PutMapping
    public ResponseEntity<Void> update(@RequestBody UpdateUserDefaultModelRequest request) {
        Long userId = currentUserId();
        if (userId == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "未登录用户无法设置默认模型");
        }

        String source = request.source();
        if (source == null || source.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "source 不能为空（SYSTEM 或 USER_KEY）");
        }
        String normalized = source.toUpperCase();

        UserDefaultModel entity = userDefaultModelRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserDefaultModel udm = new UserDefaultModel();
                    udm.setUserId(userId);
                    return udm;
                });

        switch (normalized) {
            case "SYSTEM" -> {
                Long systemModelId = request.systemModelId();
                if (systemModelId == null) {
                    throw new ResponseStatusException(BAD_REQUEST, "source=SYSTEM 时必须提供 systemModelId");
                }
                SystemModel model = systemModelRepository.findById(systemModelId)
                        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "系统模型不存在: " + systemModelId));
                entity.setSource("SYSTEM");
                entity.setSystemModel(model);
                entity.setUserApiKey(null);
            }
            case "USER_KEY" -> {
                Long keyId = request.userApiKeyId();
                if (keyId == null) {
                    throw new ResponseStatusException(BAD_REQUEST, "source=USER_KEY 时必须提供 userApiKeyId");
                }
                UserApiKey key = userApiKeyRepository.findByIdAndUserId(keyId, userId)
                        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "用户 API Key 不存在或不属于当前用户: " + keyId));
                entity.setSource("USER_KEY");
                entity.setUserApiKey(key);
                entity.setSystemModel(null);
            }
            default -> throw new ResponseStatusException(BAD_REQUEST, "非法 source 值: " + source);
        }

        userDefaultModelRepository.save(entity);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) {
            return id;
        }
        return null;
    }
}

