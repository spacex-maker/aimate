package com.openforge.aimate.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.config.SystemConfigService;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.llm.LlmClient;
import com.openforge.aimate.llm.LlmProperties;
import com.openforge.aimate.llm.model.ChatRequest;
import com.openforge.aimate.llm.model.ChatResponse;
import com.openforge.aimate.llm.model.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.http.HttpClient;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SystemModelChatHelper {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;

    public String chatText(SystemModel m, String system, String user) {
        return chatMessages(m, List.of(Message.system(system), Message.user(user)), 0.3, 1024);
    }

    /** 多轮消息（群聊 Agent 等）；temperature/maxTokens 可按场景覆盖。 */
    public String chatMessages(SystemModel m, List<Message> messages, double temperature, int maxTokens) {
        LlmProperties.ProviderConfig config = buildConfig(m);
        LlmClient client = new LlmClient(httpClient, objectMapper, config);
        ChatRequest req = ChatRequest.builder()
                .model(config.model())
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
        ChatResponse resp = client.chat(req);
        Message msg = resp.firstMessage();
        return msg != null && StringUtils.hasText(msg.content()) ? msg.content().trim() : "";
    }

    public LlmProperties.ProviderConfig buildConfig(SystemModel m) {
        String provider = m.getProvider() != null ? m.getProvider().toLowerCase().trim() : "openai";
        String baseUrl = m.getBaseUrl();
        String model = m.getModelId();
        String keyCfg = m.getApiKeyConfigKey();
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
            throw new IllegalStateException("SystemModel 缺少 base_url 或 model_id");
        }
        String apiKey = null;
        if (StringUtils.hasText(keyCfg)) {
            apiKey = systemConfigService.get(keyCfg).orElse(null);
            if (!StringUtils.hasText(apiKey)) {
                apiKey = keyCfg;
            }
        }
        if (!StringUtils.hasText(apiKey)) {
            apiKey = systemConfigService.defaultApiKeyForProvider(provider).orElse(null);
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("SystemModel 未配置 API Key");
        }
        return new LlmProperties.ProviderConfig(provider, baseUrl, apiKey, model, 120);
    }
}
