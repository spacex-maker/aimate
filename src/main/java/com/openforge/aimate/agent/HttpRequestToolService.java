package com.openforge.aimate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 服务端原生 HTTP 工具：不依赖用户 Docker 容器，供 Agent 调用外部 API（如 Soul、公开 HTTPS）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HttpRequestToolService {

    private static final int MAX_BODY_CHARS = 80_000;
    private static final int DEFAULT_TIMEOUT_SEC = 30;
    private static final int MAX_TIMEOUT_SEC = 60;
    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public String execute(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
            String method = args.path("method").asText("GET").trim().toUpperCase(Locale.ROOT);
            String url = args.path("url").asText("").trim();
            if (url.isEmpty()) {
                return "[ToolError] http_request 需要非空的 url。";
            }
            if (!ALLOWED_METHODS.contains(method)) {
                return "[ToolError] http_request 不支持 method=" + method
                        + "。允许: " + String.join(", ", ALLOWED_METHODS);
            }

            URI uri;
            try {
                uri = URI.create(url);
            } catch (Exception e) {
                return "[ToolError] url 非法: " + e.getMessage();
            }
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return "[ToolError] 仅支持 http/https。";
            }
            String blockReason = ssrfBlockReason(uri);
            if (blockReason != null) {
                return "[ToolError] " + blockReason;
            }

            int timeoutSec = args.path("timeout_seconds").asInt(DEFAULT_TIMEOUT_SEC);
            timeoutSec = Math.min(MAX_TIMEOUT_SEC, Math.max(5, timeoutSec));

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(timeoutSec));

            JsonNode headers = args.path("headers");
            if (headers.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = headers.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    String name = e.getKey() == null ? "" : e.getKey().trim();
                    if (name.isEmpty()) continue;
                    String lower = name.toLowerCase(Locale.ROOT);
                    if ("host".equals(lower) || "content-length".equals(lower)) continue;
                    builder.header(name, e.getValue().asText(""));
                }
            }

            String body = args.path("body").asText(null);
            boolean hasBody = body != null && !body.isEmpty()
                    && !"GET".equals(method) && !"HEAD".equals(method);
            if (hasBody) {
                if (headers.path("Content-Type").isMissingNode()
                        && headers.path("content-type").isMissingNode()) {
                    builder.header("Content-Type", "application/json; charset=utf-8");
                }
                builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            String respBody = response.body() == null ? "" : response.body();
            boolean truncated = respBody.length() > MAX_BODY_CHARS;
            if (truncated) {
                respBody = respBody.substring(0, MAX_BODY_CHARS) + "\n…[truncated]";
            }

            StringBuilder out = new StringBuilder();
            out.append("HTTP ").append(response.statusCode()).append('\n');
            out.append("url: ").append(url).append('\n');
            if (truncated) {
                out.append("(body truncated to ").append(MAX_BODY_CHARS).append(" chars)\n");
            }
            out.append("---\n");
            out.append(respBody);
            return out.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[ToolError] http_request 被中断";
        } catch (Exception e) {
            log.warn("[http_request] failed: {}", e.getMessage());
            return "[ToolError] http_request 失败: " + e.getMessage();
        }
    }

    /** @return null if ok, otherwise human-readable block reason */
    private String ssrfBlockReason(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "url 缺少 host。";
        }
        String h = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(h) || h.endsWith(".localhost") || h.endsWith(".local")
                || "metadata.google.internal".equals(h)) {
            return "禁止访问本机/内网主机: " + host;
        }
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (InetAddress addr : addrs) {
                if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                        || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                        || addr.isMulticastAddress()) {
                    return "禁止访问私网/环回地址: " + addr.getHostAddress();
                }
                byte[] b = addr.getAddress();
                // 169.254.169.254 cloud metadata
                if (b.length == 4 && (b[0] & 0xff) == 169 && (b[1] & 0xff) == 254) {
                    return "禁止访问链路本地/元数据地址: " + addr.getHostAddress();
                }
            }
        } catch (Exception e) {
            return "无法解析主机 " + host + ": " + e.getMessage();
        }
        return null;
    }
}
