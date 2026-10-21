package com.openforge.aimate.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.config.OidcProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * 核销 AIMATEX Account 控制台签发的一次性超管票据。
 */
@Service
public class StaffHandoffService {

    private final OidcProperties oidcProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String platformCode;

    public StaffHandoffService(
            OidcProperties oidcProperties,
            ObjectMapper objectMapper,
            @Value("${aimatex.platform-code:chat}") String platformCode
    ) {
        this.oidcProperties = oidcProperties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
        this.platformCode = platformCode;
    }

    public HandoffClaims consume(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            throw new IllegalArgumentException("缺少 ticket");
        }
        String issuer = oidcProperties.getIssuer();
        if (!StringUtils.hasText(issuer)) {
            throw new IllegalArgumentException("未配置账号中心地址");
        }
        String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        JsonNode body;
        try {
            String raw = restClient.post()
                    .uri(base + "/api/integration/staff-handoff/consume")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("ticket", ticket.trim()))
                    .retrieve()
                    .body(String.class);
            body = raw == null ? null : objectMapper.readTree(raw);
        } catch (RestClientResponseException ex) {
            throw new IllegalArgumentException(messageOf(ex));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法校验统一登录票据");
        }
        if (body == null || !body.hasNonNull("email")) {
            throw new IllegalArgumentException("票据校验失败");
        }
        String claimed = text(body, "platformCode");
        if (StringUtils.hasText(platformCode)
                && StringUtils.hasText(claimed)
                && !platformCode.equalsIgnoreCase(claimed)) {
            throw new IllegalArgumentException("票据平台不匹配");
        }
        String email = text(body, "email");
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("票据无效");
        }
        return new HandoffClaims(email, text(body, "displayName"));
    }

    private String messageOf(RestClientResponseException ex) {
        try {
            JsonNode node = objectMapper.readTree(ex.getResponseBodyAsString());
            if (node.hasNonNull("message")) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {
            // keep default
        }
        return "票据无效";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    public record HandoffClaims(String email, String displayName) {}
}
