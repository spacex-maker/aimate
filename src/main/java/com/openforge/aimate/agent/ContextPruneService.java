package com.openforge.aimate.agent;

import com.openforge.aimate.domain.SessionMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L0 确定性裁剪：压缩前对「老化用稿」做规则瘦身，不改 UI 原文。
 * 典型可砍 15–30%：重复 tool 读、已解决错误、超长 payload。
 */
@Service
public class ContextPruneService {

    private static final int TOOL_CONTENT_MAX = 800;
    private static final Pattern URL_OR_PATH = Pattern.compile(
            "(https?://\\S+)|([A-Za-z]:\\\\[^\\s\"']+)|((?:/[\\w.-]+){2,})|((?:[\\w.-]+/){2,}[\\w.-]+)"
    );

    /**
     * 为摘要模型生成裁剪后的行文本（role: content），不写回 DB。
     */
    public List<String> pruneForSummarizer(List<SessionMessage> messages) {
        if (messages == null || messages.isEmpty()) return List.of();

        // 自后向前：同 key 的 tool 结果只保留最后一次全文
        Map<String, Integer> lastFullIndex = new HashMap<>();
        List<String> keys = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            SessionMessage m = messages.get(i);
            String key = toolDedupeKey(m);
            keys.add(key);
            if (key != null && "tool".equalsIgnoreCase(m.getRole())) {
                lastFullIndex.put(key, i);
            }
        }

        // 已解决错误：后续同 key 有成功结果则更早失败截断
        Set<String> keysWithSuccessAfter = new HashSet<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            SessionMessage m = messages.get(i);
            String key = keys.get(i);
            if (key == null || !"tool".equalsIgnoreCase(m.getRole())) continue;
            String c = m.getContent() != null ? m.getContent() : "";
            boolean err = looksLikeError(c);
            if (!err) {
                keysWithSuccessAfter.add(key);
            }
        }

        List<String> lines = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            SessionMessage m = messages.get(i);
            String role = m.getRole() != null ? m.getRole() : "?";
            String content = m.getContent() != null ? m.getContent() : "";
            String key = keys.get(i);

            if ("assistant".equalsIgnoreCase(role) && StringUtils.hasText(m.getToolCallsJson())) {
                content = (content.isBlank() ? "" : content + "\n")
                        + "[tool_calls] " + truncate(m.getToolCallsJson(), 400);
            }

            if ("tool".equalsIgnoreCase(role)) {
                String prefix = "[tool_result id=" + (m.getToolCallId() != null ? m.getToolCallId() : "") + "] ";
                boolean isLastFull = key != null && lastFullIndex.getOrDefault(key, -1) == i;
                boolean resolvedError = key != null && looksLikeError(content) && keysWithSuccessAfter.contains(key);
                if (!isLastFull && key != null) {
                    content = prefix + "[earlier duplicate of " + key + "; see latest]";
                } else if (resolvedError) {
                    content = prefix + truncate(firstLine(content), 120) + " [resolved later]";
                } else {
                    content = prefix + truncate(content, TOOL_CONTENT_MAX);
                }
            } else if (content.length() > 1_200) {
                content = truncate(content, 1_200);
            }

            lines.add(role + ": " + content);
        }
        return lines;
    }

    /** transcript 是否明显含 path/url（用于摘要校验）。 */
    public boolean transcriptHasPathsOrUrls(String transcript) {
        if (!StringUtils.hasText(transcript)) return false;
        Matcher m = URL_OR_PATH.matcher(transcript);
        int hits = 0;
        while (m.find()) {
            hits++;
            if (hits >= 2) return true;
        }
        return hits >= 1 && transcript.length() > 200;
    }

    private static String toolDedupeKey(SessionMessage m) {
        if (m == null || !"tool".equalsIgnoreCase(m.getRole())) return null;
        String c = m.getContent() != null ? m.getContent() : "";
        Matcher matcher = URL_OR_PATH.matcher(c);
        if (matcher.find()) {
            return matcher.group().toLowerCase(Locale.ROOT);
        }
        // 回退：tool_call_id 前缀（同名工具多次调用不完全去重）
        if (StringUtils.hasText(m.getToolCallId()) && m.getToolCallId().length() >= 8) {
            return "tc:" + m.getToolCallId().substring(0, 8);
        }
        return null;
    }

    private static boolean looksLikeError(String c) {
        if (c == null) return false;
        String lower = c.toLowerCase(Locale.ROOT);
        return lower.startsWith("[toolerror]")
                || lower.contains("error:")
                || lower.contains("failed:")
                || lower.contains("exception");
    }

    private static String firstLine(String c) {
        if (c == null) return "";
        int nl = c.indexOf('\n');
        return nl < 0 ? c.trim() : c.substring(0, nl).trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
