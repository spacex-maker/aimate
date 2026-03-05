package com.openforge.aimate.agent;

import com.openforge.aimate.domain.UserToolSettings;
import com.openforge.aimate.repository.UserToolSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户系统工具开关的读写，未设置时返回全开默认值。
 */
@Service
@RequiredArgsConstructor
public class UserToolSettingsService {

    private final UserToolSettingsRepository repository;

    /**
     * 获取用户工具设置，若不存在则创建并返回默认（全部 true）。
     */
    @Transactional(readOnly = true)
    public UserToolSettings getOrCreate(Long userId) {
        if (userId == null) return defaultSettings();
        return repository.findByUserId(userId).orElse(null);
    }

    /** 若 getOrCreate 返回 null（无记录），可调用此方法在独立事务中创建默认并返回。 */
    @Transactional
    public UserToolSettings ensureAndGet(Long userId) {
        if (userId == null) return defaultSettings();
        UserToolSettings s = repository.findByUserId(userId).orElse(null);
        if (s != null) return s;
        s = UserToolSettings.builder()
                .userId(userId)
                .memoryEnabled(true)
                .webSearchEnabled(true)
                .createToolEnabled(true)
                .scriptExecEnabled(true)
                .build();
        return repository.save(s);
    }

    @Transactional
    public UserToolSettings update(Long userId, boolean memoryEnabled, boolean webSearchEnabled,
                                   boolean createToolEnabled, boolean scriptExecEnabled) {
        if (userId == null) return defaultSettings();
        UserToolSettings s = ensureAndGet(userId);
        s.setMemoryEnabled(memoryEnabled);
        s.setWebSearchEnabled(webSearchEnabled);
        s.setCreateToolEnabled(createToolEnabled);
        s.setScriptExecEnabled(scriptExecEnabled);
        return repository.save(s);
    }

    private static UserToolSettings defaultSettings() {
        return UserToolSettings.builder()
                .userId(null)
                .memoryEnabled(true)
                .webSearchEnabled(true)
                .createToolEnabled(true)
                .scriptExecEnabled(true)
                .build();
    }
}
