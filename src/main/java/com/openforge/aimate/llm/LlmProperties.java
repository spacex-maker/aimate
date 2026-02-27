package com.openforge.aimate.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Externalised LLM provider configuration.
 *
 * Reads from application.yml under the "agent.llm" prefix:
 *
 * agent:
 *   llm:
 *     primary:
 *       name: gpt-4o
 *       base-url: https://api.openai.com/v1
 *       api-key: sk-...
 *       model: gpt-4o
 *       timeout-seconds: 120
 *     fallback:
 *       name: deepseek-chat
 *       base-url: https://api.deepseek.com/v1
 *       api-key: sk-...
 *       model: deepseek-chat
 *       timeout-seconds: 120
 */
@ConfigurationProperties(prefix = "agent.llm")
public record LlmProperties(
        ProviderConfig primary,
        ProviderConfig fallback
) {

    public record ProviderConfig(
            String name,
            String baseUrl,
            String apiKey,
            String model,
            @DefaultValue("120") int timeoutSeconds
    ) {}
}
