package com.openforge.aimate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 调用 Tavily Search API，供 Agent 的 tavily_search 工具使用。
 * API Key 由调用方从系统配置表传入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TavilySearchService {

    private static final String TAVILY_SEARCH_URL = "https://api.tavily.com/search";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;

    /**
     * 执行搜索，返回格式化给 LLM 的文本（标题、URL、摘要）。
     *
     * @param apiKey      Tavily API Key（从系统配置 TAVILY_API_KEY 读取）
     * @param query       搜索关键词
     * @param maxResults  最多返回条数 1–20，默认 5
     * @param searchDepth basic | advanced | fast | ultra-fast，默认 basic
     * @param topic       general | news | finance，默认 general
     * @return 格式化后的搜索结果，失败时返回错误信息字符串
     */
    public String search(String apiKey, String query, int maxResults, String searchDepth, String topic) {
        if (apiKey == null || apiKey.isBlank()) {
            return "[ToolError] Tavily API Key 未配置。请在系统配置表中设置 TAVILY_API_KEY。";
        }
        if (query == null || query.isBlank()) {
            return "[ToolError] tavily_search 需要非空的 query 参数。";
        }
        maxResults = Math.min(20, Math.max(1, maxResults));
        if (searchDepth == null || searchDepth.isBlank()) searchDepth = "basic";
        if (topic == null || topic.isBlank()) topic = "general";

        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "query", query,
                    "max_results", maxResults,
                    "search_depth", searchDepth,
                    "topic", topic
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TAVILY_SEARCH_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpClient client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.warn("[Tavily] API returned {}: {}", response.statusCode(), response.body());
                return "[ToolError] Tavily 搜索请求失败: HTTP " + response.statusCode() + ". 请检查 API Key 或稍后重试。";
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return "未找到与 \"" + query + "\" 相关的搜索结果。";
            }

            List<String> lines = new ArrayList<>();
            lines.add("## Tavily 搜索结果 (query: " + query + ")");
            int i = 1;
            for (JsonNode item : results) {
                String title = item.path("title").asText("");
                String url = item.path("url").asText("");
                String content = item.path("content").asText("").trim();
                if (title.isEmpty() && content.isEmpty()) continue;
                lines.add("");
                lines.add("### " + i + ". " + (title.isEmpty() ? "(无标题)" : title));
                if (!url.isEmpty()) lines.add("URL: " + url);
                if (!content.isEmpty()) lines.add(content);
                i++;
            }
            return String.join("\n", lines);
        } catch (Exception e) {
            log.warn("[Tavily] Search failed: {}", e.getMessage());
            return "[ToolError] Tavily 搜索异常: " + e.getMessage();
        }
    }
}
