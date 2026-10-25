package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话结构化滚动状态（写入 agent_sessions.context_summary）。
 * 对齐 Claude Code / Factory compact：固定字段，缺项用空列表，禁止空散文漂移。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionState(
        String intent,
        List<String> goals,
        List<String> decisions,
        List<String> constraints,
        /** ids / paths / urls / names — 存引用，不存代码原文 */
        List<String> entities,
        List<String> openTasks,
        List<String> toolOutcomes,
        List<String> nextSteps
) {
    public static SessionState empty() {
        return new SessionState(null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public SessionState normalized() {
        return new SessionState(
                blankToNull(intent),
                copyList(goals),
                copyList(decisions),
                copyList(constraints),
                copyList(entities),
                copyList(openTasks),
                copyList(toolOutcomes),
                copyList(nextSteps)
        );
    }

    public int entityCount() {
        return entities == null ? 0 : entities.size();
    }

    public int constraintCount() {
        return constraints == null ? 0 : constraints.size();
    }

    /** 供送模：稳定前缀 + JSON，便于后续 prompt cache。 */
    public String toPromptBlock(ObjectMapper mapper) {
        try {
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(normalized());
            return "[Session state — compressed; keep using these facts]\n" + json;
        } catch (Exception e) {
            return "[Session state — compressed; keep using these facts]\n" + fallbackText();
        }
    }

    public String toJson(ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(normalized());
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 解析 context_summary：优先 JSON；兼容旧版自由文本（塞进 intent）。
     */
    public static SessionState parse(String raw, ObjectMapper mapper) {
        if (!StringUtils.hasText(raw)) {
            return empty();
        }
        String s = raw.trim();
        // 去掉旧版 system 头
        if (s.startsWith("[Earlier conversation summary")) {
            int nl = s.indexOf('\n');
            if (nl > 0) s = s.substring(nl + 1).trim();
        }
        if (s.startsWith("[Session state")) {
            int nl = s.indexOf('\n');
            if (nl > 0) s = s.substring(nl + 1).trim();
        }
        if (s.startsWith("{")) {
            try {
                SessionState st = mapper.readValue(s, SessionState.class);
                return st != null ? st.normalized() : empty();
            } catch (Exception ignored) {
                // fall through
            }
        }
        return new SessionState(s.length() > 800 ? s.substring(0, 800) + "…" : s,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()).normalized();
    }

    private String fallbackText() {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(intent)) sb.append("intent: ").append(intent).append('\n');
        appendSection(sb, "goals", goals);
        appendSection(sb, "decisions", decisions);
        appendSection(sb, "constraints", constraints);
        appendSection(sb, "entities", entities);
        appendSection(sb, "open_tasks", openTasks);
        appendSection(sb, "tool_outcomes", toolOutcomes);
        appendSection(sb, "next_steps", nextSteps);
        return sb.toString().trim();
    }

    private static void appendSection(StringBuilder sb, String title, List<String> items) {
        if (items == null || items.isEmpty()) return;
        sb.append(title).append(":\n");
        for (String it : items) {
            if (StringUtils.hasText(it)) sb.append("- ").append(it.trim()).append('\n');
        }
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static List<String> copyList(List<String> in) {
        if (in == null || in.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : in) {
            if (StringUtils.hasText(s)) out.add(s.trim());
        }
        return List.copyOf(out);
    }
}
