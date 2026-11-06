package com.openforge.aimate.config;

import com.openforge.aimate.domain.SystemConfig;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.domain.SystemProvider;
import com.openforge.aimate.repository.SystemConfigRepository;
import com.openforge.aimate.repository.SystemModelRepository;
import com.openforge.aimate.repository.SystemProviderRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One LLM provider owns one API key. Models resolve keys via provider code.
 */
@Service
@Order(40)
public class SystemProviderService implements ApplicationRunner {

    private static final Map<String, String> DEFAULT_NAMES = Map.of(
            "deepseek", "DeepSeek",
            "openai", "OpenAI",
            "google", "Google",
            "anthropic", "Anthropic",
            "xai", "xAI",
            "aimodel", "AIMATEX Aimodel"
    );

    private final SystemProviderRepository providerRepository;
    private final SystemModelRepository systemModelRepository;
    private final SystemConfigRepository systemConfigRepository;

    public SystemProviderService(
            SystemProviderRepository providerRepository,
            SystemModelRepository systemModelRepository,
            SystemConfigRepository systemConfigRepository
    ) {
        this.providerRepository = providerRepository;
        this.systemModelRepository = systemModelRepository;
        this.systemConfigRepository = systemConfigRepository;
    }

    public Optional<String> resolveApiKey(String providerCode, String legacyConfigKey) {
        if (StringUtils.hasText(providerCode)) {
            Optional<String> fromProvider = providerRepository.findByCodeIgnoreCase(providerCode.trim())
                    .map(SystemProvider::getApiKey)
                    .filter(StringUtils::hasText);
            if (fromProvider.isPresent()) {
                return fromProvider;
            }
        }
        if (StringUtils.hasText(legacyConfigKey)) {
            String key = legacyConfigKey.trim();
            Optional<String> fromConfig = systemConfigRepository.findByConfigKey(key)
                    .map(SystemConfig::getConfigValue)
                    .filter(StringUtils::hasText);
            if (fromConfig.isPresent()) {
                return fromConfig;
            }
            if (looksLikeRawKey(key)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    private static boolean looksLikeRawKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.startsWith("sk-") || lower.startsWith("amk_") || lower.startsWith("tvly-");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureFromModels();
        migrateLegacyKeys();
    }

    private void ensureFromModels() {
        Map<String, Integer> codes = new LinkedHashMap<>();
        for (SystemModel m : systemModelRepository.findAll()) {
            if (!StringUtils.hasText(m.getProvider())) continue;
            String code = m.getProvider().trim().toLowerCase(Locale.ROOT);
            codes.putIfAbsent(code, m.getSortOrder());
        }
        for (Map.Entry<String, String> e : DEFAULT_NAMES.entrySet()) {
            codes.putIfAbsent(e.getKey(), 100);
        }
        int i = 0;
        for (Map.Entry<String, Integer> e : codes.entrySet()) {
            String code = e.getKey();
            if (providerRepository.existsByCodeIgnoreCase(code)) continue;
            SystemProvider p = new SystemProvider();
            p.setCode(code);
            p.setDisplayName(DEFAULT_NAMES.getOrDefault(code, title(code)));
            p.setSortOrder(e.getValue() != null ? e.getValue() : (10 + i * 10));
            p.setEnabled(true);
            p.setDescription(switch (code) {
                case "aimodel" -> "Local / Aimodel gateway models";
                case "deepseek" -> "DeepSeek official API";
                default -> null;
            });
            providerRepository.save(p);
            i++;
        }
    }

    private void migrateLegacyKeys() {
        copyConfigToProvider("DEEPSEEK_API_KEY", "deepseek");
        copyConfigToProvider("DEEPSEEK_API_KEY_AIMODEL_CHAT", "aimodel");
    }

    private void copyConfigToProvider(String configKey, String providerCode) {
        systemConfigRepository.findByConfigKey(configKey).ifPresent(cfg -> {
            if (!StringUtils.hasText(cfg.getConfigValue())) return;
            SystemProvider p = providerRepository.findByCodeIgnoreCase(providerCode).orElseGet(() -> {
                SystemProvider created = new SystemProvider();
                created.setCode(providerCode);
                created.setDisplayName(DEFAULT_NAMES.getOrDefault(providerCode, title(providerCode)));
                created.setEnabled(true);
                created.setSortOrder(60);
                return created;
            });
            if (!StringUtils.hasText(p.getApiKey())) {
                p.setApiKey(cfg.getConfigValue().trim());
                if (!StringUtils.hasText(p.getDescription()) && StringUtils.hasText(cfg.getDescription())) {
                    p.setDescription(cfg.getDescription());
                }
                providerRepository.save(p);
            }
        });
    }

    private static String title(String code) {
        if (!StringUtils.hasText(code)) return "Provider";
        return code.substring(0, 1).toUpperCase(Locale.ROOT) + code.substring(1);
    }
}