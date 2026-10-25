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

    /**
     * 列出某 UserAgent 绑定 Soul 上的记忆条目（供「我的 Agent」内联预览）。
     * 不返回 accessToken。
     */
    public Map<String, Object> listMemories(Long userId, Long userAgentId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("items", List.of());
        out.put("total", 0);
        if (!isEnabled() || userId == null || userAgentId == null) {
            out.put("soulEnabled", isEnabled());
            return out;
        }
        UserAgent ua = userAgentRepository.findById(userAgentId).orElse(null);
        if (ua == null || !userId.equals(ua.getUserId())) {
            out.put("ok", false);
            out.put("error", "Agent 不存在");
            return out;
        }
        out.put("soulEnabled", true);
        out.put("soulAgentId", ua.getSoulAgentId());
        out.put("soulBound", ua.getSoulAgentId() != null && StringUtils.hasText(ua.getSoulAccessToken()));
        out.put("externalAgentKey", ua.getExternalAgentKey());
        String token = resolveToken(userId, userAgentId);
        if (!StringUtils.hasText(token)) {
            out.put("ok", false);
            out.put("error", "尚未绑定 Soul 或令牌不可用");
            return out;
        }
        try {
            String url = trimSlash(props.getApiBase()) + "/memories";
            ResponseEntity<String> resp = http().exchange(url, HttpMethod.GET, authEntity(token, null), String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode items = root.path("items");
            List<Map<String, Object>> list = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode n : items) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    if (n.has("id") && !n.path("id").isNull()) row.put("id", n.path("id").asLong());
                    row.put("content", n.path("content").asText(""));
                    row.put("kind", n.path("kind").asText("FACT"));
                    row.put("scope", n.path("scope").asText(null));
                    row.put("project", n.path("project").asText(null));
                    row.put("importance", n.path("importance").asDouble(0.5));
                    row.put("pinned", n.path("pinned").asBoolean(false));
                    row.put("status", n.path("status").asText(null));
                    String updatedAt = n.path("updatedAt").asText(null);
                    if (!StringUtils.hasText(updatedAt)) {
                        updatedAt = n.path("updated_at").asText(null);
                    }
                    row.put("updatedAt", updatedAt);
                    String content = (String) row.get("content");
                    if (StringUtils.hasText(content)) {
                        list.add(row);
                    }
                }
            }
            out.put("items", list);
            out.put("total", list.size());
            return out;
        } catch (Exception e) {
            log.warn("[SoulMemory] list failed user={} agent={}: {}", userId, userAgentId, e.toString());
            out.put("ok", false);
            out.put("error", "读取 Soul 记忆失败");
            return out;
        }
    }

    public void store(String content, String kind, float importance, Long userId) {
        store(content, kind, importance, userId, null, null);
    }

    public void store(String content, String kind, float importance, Long userId, Long userAgentId) {
        store(content, kind, importance, userId, userAgentId, null);
    }

    /** @param project 可选项目锚点，如 group:g_xxx，便于按群召回 */
    public void store(
            String content,
            String kind,
            float importance,
            Long userId,
            Long userAgentId,
            String project
    ) {
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
            if (StringUtils.hasText(project)) {
                body.put("project", project.trim());
            }
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

    /**
     * 本地桥接：用 Agent 自有 Soul token 代理 GET/POST（不把 token 下发给本机提示词）。
     * path 相对 soul apiBase，如 {@code /me}、{@code /context?q=xx}、{@code /memories}。
     */
    public Map<String, Object> proxyAgentSoul(
            Long userId,
            Long userAgentId,
            HttpMethod method,
            String relativePath,
            Object body
    ) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!isEnabled()) {
            out.put("ok", false);
            out.put("error", "Soul 记忆未启用");
            return out;
        }
        UserAgent ua = userAgentRepository.findById(userAgentId).orElse(null);
        if (ua == null || !userId.equals(ua.getUserId())) {
            out.put("ok", false);
            out.put("error", "Agent 不存在");
            return out;
        }
        String token = resolveToken(userId, userAgentId);
        if (!StringUtils.hasText(token)) {
            out.put("ok", false);
            out.put("error", "尚未绑定 Soul 或令牌不可用");
            return out;
        }
        String path = relativePath == null ? "" : relativePath.trim();
        if (!path.startsWith("/")) path = "/" + path;
        if (path.contains("..")) {
            out.put("ok", false);
            out.put("error", "非法路径");
            return out;
        }
        try {
            String url = trimSlash(props.getApiBase()) + path;
            ResponseEntity<String> resp = http().exchange(url, method, authEntity(token, body), String.class);
            out.put("ok", true);
            out.put("httpStatus", resp.getStatusCode().value());
            if (StringUtils.hasText(resp.getBody())) {
                JsonNode root = objectMapper.readTree(resp.getBody());
                out.put("data", objectMapper.convertValue(root, Map.class));
            } else {
                out.put("data", Map.of());
            }
            return out;
        } catch (Exception e) {
            log.warn("[SoulMemory] proxy failed agent={} path={}: {}", userAgentId, path, e.toString());
            out.put("ok", false);
            out.put("error", "Soul 代理请求失败");
            return out;
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
            case "SEMANTIC", "PROCEDURAL", "FACT" -> "FACT";
            case "EPISODIC", "EPISODE" -> "EPISODE";
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
