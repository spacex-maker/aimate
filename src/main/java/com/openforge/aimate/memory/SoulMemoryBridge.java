package com.openforge.aimate.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.repository.UserAgentRepository;
import com.openforge.aimate.repository.UserRepository;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat → Soul 记忆桥：按 UserAgent 使用独立 USER 魂 token；兼容旧 PLATFORM ensure。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoulMemoryBridge {

    private final SoulMemoryProperties props;
    private final UserRepository userRepository;
    private final UserAgentRepository userAgentRepository;
    private final ObjectMapper objectMapper;

    /** userAgentId → accessToken */
    private final ConcurrentHashMap<Long, String> tokenByAgent = new ConcurrentHashMap<>();
    /** 兼容旧路径：userId → PLATFORM token */
    private final ConcurrentHashMap<Long, String> tokenByUser = new ConcurrentHashMap<>();

    private RestTemplate http() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(props.getConnectTimeoutMs());
        f.setReadTimeout(props.getReadTimeoutMs());
        return new RestTemplate(f);
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    public List<MemoryRecord> recall(String query, int topK, Long userId) {
        return recall(query, topK, userId, null);
    }

    public List<MemoryRecord> recall(String query, int topK, Long userId, Long userAgentId) {
        if (!isEnabled() || userId == null) {
            return List.of();
        }
        String token = resolveToken(userId, userAgentId);
        if (!StringUtils.hasText(token)) {
            return List.of();
        }
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(trimSlash(props.getApiBase()) + "/recall")
                    .queryParam("q", query == null ? "" : query)
                    .queryParam("limit", Math.max(1, Math.min(50, topK)))
                    .toUriString();
            ResponseEntity<String> resp = http().exchange(url, HttpMethod.GET, authEntity(token, null), String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode items = root.path("items");
            List<MemoryRecord> out = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode n : items) {
                    String content = n.path("content").asText("");
                    if (content.isBlank()) continue;
                    double score = n.path("score").asDouble(0.5);
                    float importance = (float) n.path("importance").asDouble(0.6);
                    out.add(new MemoryRecord(
                            content,
                            mapKind(n.path("kind").asText("FACT")),
                            null,
                            importance,
                            score
                    ));
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("[SoulMemory] recall failed user={} agent={}: {}", userId, userAgentId, e.toString());
            return List.of();
        }
    }

    public void store(String content, String kind, float importance, Long userId) {
        store(content, kind, importance, userId, null);
    }

    public void store(String content, String kind, float importance, Long userId, Long userAgentId) {
        if (!isEnabled() || userId == null || !StringUtils.hasText(content)) {
            return;
        }
        String token = resolveToken(userId, userAgentId);
        if (!StringUtils.hasText(token)) {
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("content", content.trim());
            body.put("kind", mapToSoulKind(kind));
            body.put("importance", Math.max(0, Math.min(1, importance)));
            body.put("client", Map.of(
                    "app", "aimatex-chat",
                    "device", hostname()
            ));
            String url = trimSlash(props.getApiBase()) + "/memories";
            http().exchange(url, HttpMethod.POST, authEntity(token, body), String.class);
        } catch (Exception e) {
            log.warn("[SoulMemory] store failed user={} agent={}: {}", userId, userAgentId, e.toString());
        }
    }

    private String resolveToken(Long userId, Long userAgentId) {
        if (userAgentId != null) {
            String cached = tokenByAgent.get(userAgentId);
            if (StringUtils.hasText(cached)) return cached;
            UserAgent ua = userAgentRepository.findById(userAgentId).orElse(null);
            if (ua != null && StringUtils.hasText(ua.getSoulAccessToken())) {
                tokenByAgent.put(userAgentId, ua.getSoulAccessToken());
                return ua.getSoulAccessToken();
            }
        }
        return ensurePlatformToken(userId);
    }

    /** 旧 PLATFORM 路径；新 Agent 应带 soulAccessToken。 */
    public String ensurePlatformToken(Long userId) {
        if (userId == null) return null;
        String cached = tokenByUser.get(userId);
        if (StringUtils.hasText(cached)) return cached;

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !StringUtils.hasText(user.getAimatexAccountSub())) {
            log.debug("[SoulMemory] skip ensure: user {} missing account sub", userId);
            return null;
        }
        if (!StringUtils.hasText(props.getPlatformKey())) {
            log.warn("[SoulMemory] platform key not configured");
            return null;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("accountSub", user.getAimatexAccountSub());
            body.put("platformCode", props.getPlatformCode());
            body.put("displayName", user.getDisplayName());
            body.put("email", user.getEmail());
            body.put("issueToken", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Soul-Platform-Key", props.getPlatformKey());
            ResponseEntity<String> resp = http().exchange(
                    props.getPlatformEnsureUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            JsonNode root = objectMapper.readTree(resp.getBody());
            String token = root.path("accessToken").asText(null);
            if (StringUtils.hasText(token)) {
                tokenByUser.put(userId, token);
                return token;
            }
        } catch (Exception e) {
            log.warn("[SoulMemory] ensure failed user={}: {}", userId, e.toString());
        }
        return null;
    }

    /** @deprecated 使用 ensurePlatformToken 或 UserAgent.soulAccessToken */
    @Deprecated
    public String ensureToken(Long userId) {
        return ensurePlatformToken(userId);
    }

    public void cacheAgentToken(Long userAgentId, String token) {
        if (userAgentId != null && StringUtils.hasText(token)) {
            tokenByAgent.put(userAgentId, token);
        }
    }

    private HttpEntity<?> authEntity(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new HttpEntity<>(body, headers);
        }
        return new HttpEntity<>(headers);
    }

    private static String mapToSoulKind(String chatType) {
        if (chatType == null) return "FACT";
        return switch (chatType.toUpperCase()) {
            case "SEMANTIC", "PROCEDURAL" -> "FACT";
            case "EPISODIC" -> "EPISODE";
            case "PROFILE" -> "PROFILE";
            case "PREFERENCE" -> "PREFERENCE";
            case "DECISION" -> "DECISION";
            default -> "FACT";
        };
    }

    private static MemoryType mapKind(String soulKind) {
        if (soulKind == null) return MemoryType.SEMANTIC;
        return switch (soulKind.toUpperCase()) {
            case "EPISODE" -> MemoryType.EPISODIC;
            case "PROFILE", "PREFERENCE", "DECISION", "FACT" -> MemoryType.SEMANTIC;
            default -> MemoryType.SEMANTIC;
        };
    }

    private static String trimSlash(String u) {
        if (u == null) return "";
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    private static String hostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "chat-host";
        }
    }
}
