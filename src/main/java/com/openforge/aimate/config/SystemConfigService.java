package com.openforge.aimate.config;

import com.openforge.aimate.domain.SystemConfig;
import com.openforge.aimate.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 读取系统配置表中的配置项（如 TAVILY_API_KEY）。
 */
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    public static final String TAVILY_API_KEY = "TAVILY_API_KEY";

    private final SystemConfigRepository systemConfigRepository;

    public Optional<String> get(String configKey) {
        return systemConfigRepository.findByConfigKey(configKey)
                .map(SystemConfig::getConfigValue)
                .filter(v -> v != null && !v.isBlank());
    }

    public Optional<String> getTavilyApiKey() {
        return get(TAVILY_API_KEY);
    }
}
