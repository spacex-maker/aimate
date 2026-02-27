package com.openforge.aimate.apikey;

import com.openforge.aimate.domain.UserApiKey;
import com.openforge.aimate.llm.LlmProperties;
import com.openforge.aimate.repository.UserApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves a user's stored API key into an {@link LlmProperties.ProviderConfig}
 * that can be used directly by {@link com.openforge.aimate.llm.LlmClient}.
 *
 * Resolution order for a given (userId, provider, keyType):
 *   1. The key marked is_default = true  (preferred)
 *   2. Any active key for that provider  (first found)
 *   3. Empty — caller falls back to system-level config
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserApiKeyResolver {

    private final UserApiKeyRepository keyRepo;

    /**
     * Find the user's default LLM key across all providers.
     * Returns the first active default LLM key found.
     */
    public Optional<LlmProperties.ProviderConfig> resolveDefaultLlm(Long userId) {
        if (userId == null) return Optional.empty();

        // 1. Try to find any key marked is_default = true and key_type = LLM
        return keyRepo.findByUserIdAndIsActiveTrue(userId).stream()
                .filter(k -> k.getKeyType() == UserApiKey.KeyType.LLM)
                .sorted((a, b) -> Boolean.compare(b.isDefault(), a.isDefault())) // default first
                .findFirst()
                .map(this::toProviderConfig);
    }

    /**
     * Find the user's default key for a specific provider and key type.
     */
    public Optional<LlmProperties.ProviderConfig> resolve(
            Long userId, String provider, UserApiKey.KeyType keyType) {
        if (userId == null) return Optional.empty();
        return keyRepo.findByUserIdAndProviderAndKeyTypeAndIsDefaultTrueAndIsActiveTrue(
                userId, provider, keyType)
                .map(this::toProviderConfig);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private LlmProperties.ProviderConfig toProviderConfig(UserApiKey key) {
        String baseUrl = key.getBaseUrl() != null && !key.getBaseUrl().isBlank()
                ? key.getBaseUrl()
                : defaultBaseUrl(key.getProvider());

        String model = key.getModel() != null && !key.getModel().isBlank()
                ? key.getModel()
                : defaultModel(key.getProvider());

        log.debug("[KeyResolver] Resolved key for userId={} provider={} model={}",
                key.getUser().getId(), key.getProvider(), model);

        return new LlmProperties.ProviderConfig(
                key.getProvider(),
                baseUrl,
                key.getKeyValue(),
                model,
                120
        );
    }

    private static String defaultBaseUrl(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai"    -> "https://api.openai.com/v1";
            case "deepseek"  -> "https://api.deepseek.com/v1";
            case "anthropic" -> "https://api.anthropic.com/v1";
            case "moonshot"  -> "https://api.moonshot.cn/v1";
            case "zhipu"     -> "https://open.bigmodel.cn/api/paas/v4";
            case "qwen"      -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            default          -> "https://api.openai.com/v1";
        };
    }

    private static String defaultModel(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai"    -> "gpt-4o";
            case "deepseek"  -> "deepseek-chat";
            case "anthropic" -> "claude-3-5-sonnet-20241022";
            case "moonshot"  -> "moonshot-v1-8k";
            case "zhipu"     -> "glm-4";
            case "qwen"      -> "qwen-plus";
            default          -> "gpt-4o";
        };
    }
}
