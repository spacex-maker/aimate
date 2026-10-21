package com.openforge.aimate.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chat → Soul 平台 S2S：ensureUser / 购席 / 建 USER 魂。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoulPlatformClient {

    private final SoulMemoryProperties props;
    private final ObjectMapper objectMapper;

    private RestTemplate http() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(props.getConnectTimeoutMs());
        f.setReadTimeout(props.getReadTimeoutMs());
        return new RestTemplate(f);
    }

    public boolean isConfigured() {
        return StringUtils.hasText(props.getPlatformKey()) && StringUtils.hasText(platformBase());
    }

    public JsonNode ensureUser(String accountSub, String displayName, String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountSub", accountSub);
        body.put("displayName", displayName);
        body.put("email", email);
        return post("/ensure-user", body);
    }

    public JsonNode purchaseSeat(
            String accountSub,
            String period,
            String source,
            String refChatOrderId,
            String displayName,
            String email
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountSub", accountSub);
        body.put("period", period);
        body.put("source", source);
        body.put("refChatOrderId", refChatOrderId);
        body.put("displayName", displayName);
        body.put("email", email);
        return post("/seats/purchase", body);
    }

    public JsonNode renewSeat(String accountSub, long seatId, String period, String refChatOrderId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountSub", accountSub);
        body.put("seatId", seatId);
        body.put("period", period);
        body.put("refChatOrderId", refChatOrderId);
        return post("/seats/renew", body);
    }

    public JsonNode createUserSoul(
            String accountSub,
            long seatId,
            String externalAgentKey,
            String name,
            String description,
            boolean issueToken
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountSub", accountSub);
        body.put("seatId", seatId);
        body.put("externalAgentKey", externalAgentKey);
        body.put("name", name);
        body.put("description", description);
        body.put("issueToken", issueToken);
        return post("/create-user", body);
    }

    private JsonNode post(String path, Map<String, Object> body) {
        if (!isConfigured()) {
            throw new IllegalStateException("Soul 平台密钥或地址未配置");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Soul-Platform-Key", props.getPlatformKey());
            String url = platformBase() + path;
            ResponseEntity<String> resp = http().exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            return objectMapper.readTree(resp.getBody());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[SoulPlatform] {} failed: {}", path, e.toString());
            throw new IllegalStateException("Soul 平台调用失败: " + e.getMessage(), e);
        }
    }

    private String platformBase() {
        String base = StringUtils.hasText(props.getPlatformBaseUrl())
                ? props.getPlatformBaseUrl()
                : props.getPlatformEnsureUrl();
        if (base == null) return "";
        String t = base.trim();
        if (t.endsWith("/ensure")) {
            t = t.substring(0, t.length() - "/ensure".length());
        }
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t;
    }
}
