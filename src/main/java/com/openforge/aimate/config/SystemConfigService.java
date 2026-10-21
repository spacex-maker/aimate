package com.openforge.aimate.config;

import com.openforge.aimate.domain.SystemConfig;
import com.openforge.aimate.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * 读取系统配置表中的配置项（如 TAVILY_API_KEY）。
 */
@Service
public class SystemConfigService {

    public static final String TAVILY_API_KEY = "TAVILY_API_KEY";
    /** 总开关。缺省为开启，关闭后对话与记忆接口都不再访问记忆系统。 */
    public static final String RAG_MEMORY_ENABLED = "RAG_MEMORY_ENABLED";

    private final SystemConfigRepository systemConfigRepository;

    public SystemConfigService(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    public boolean exists(String configKey) {
        return systemConfigRepository.findByConfigKey(configKey).isPresent();
    }

    public Optional<String> get(String configKey) {
        return systemConfigRepository.findByConfigKey(configKey)
                .map(SystemConfig::getConfigValue)
                .filter(v -> v != null && !v.isBlank());
    }

    public Optional<String> getTavilyApiKey() {
        return get(TAVILY_API_KEY);
    }

    /**
     * 模型未指定 key 时的默认值。DeepSeek 取第一条已填写的 DEEPSEEK_API_KEY。
     */
    public Optional<String> defaultApiKeyForProvider(String provider) {
        if (provider == null || !provider.equalsIgnoreCase("deepseek")) return Optional.empty();
        return systemConfigRepository.findAllByOrderByConfigKeyAsc().stream()
                .filter(c -> c.getConfigKey() != null && c.getConfigKey().matches("DEEPSEEK_API_KEY(_[A-Za-z0-9]{1,40})?"))
                .map(SystemConfig::getConfigValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    /** 未配置时视为开启，避免老环境突然丢掉记忆。 */
    public boolean isRagMemoryEnabled() {
        return get(RAG_MEMORY_ENABLED).map(SystemConfigService::truthy).orElse(true);
    }

    @Transactional
    public boolean setRagMemoryEnabled(boolean enabled) {
        SystemConfig row = systemConfigRepository.findByConfigKey(RAG_MEMORY_ENABLED).orElseGet(SystemConfig::new);
        row.setConfigKey(RAG_MEMORY_ENABLED);
        row.setConfigValue(enabled ? "true" : "false");
        row.setDescription("RAG 长期记忆总开关。关闭后对话不召回、不写入记忆。");
        systemConfigRepository.save(row);
        return enabled;
    }

    private static boolean truthy(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return !v.equals("false") && !v.equals("0") && !v.equals("off") && !v.equals("no");
    }
}
