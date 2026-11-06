package com.openforge.aimate.systemagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.agent.SystemModelChatHelper;
import com.openforge.aimate.agent.TavilySearchService;
import com.openforge.aimate.config.SystemConfigService;
import com.openforge.aimate.domain.KnowledgeBase;
import com.openforge.aimate.domain.SystemAgent;
import com.openforge.aimate.domain.SystemAgentRun;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.repository.KnowledgeBaseRepository;
import com.openforge.aimate.repository.SystemAgentRepository;
import com.openforge.aimate.repository.SystemAgentRunRepository;
import com.openforge.aimate.repository.SystemModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemAgentService {

    public static final String TYPE_KB_CURATOR = "KB_CURATOR";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final SystemAgentRepository agentRepository;
    private final SystemAgentRunRepository runRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final SystemModelRepository systemModelRepository;
    private final SystemModelChatHelper chatHelper;
    private final TavilySearchService tavilySearchService;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<Long, Boolean> runningGuard = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String q, String type, Boolean enabled) {
        return agentRepository.search(
                q == null ? "" : q.trim(),
                type == null ? "" : type.trim(),
                enabled
        ).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        return view(require(id));
    }

    @Transactional
    public Map<String, Object> save(Long id, Map<String, Object> body) {
        SystemAgent row = id != null ? require(id) : new SystemAgent();
        String code = str(body.get("code"));
        String name = str(body.get("name"));
        if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code、name 必填");
        }
        code = code.trim().toUpperCase();
        agentRepository.findByCodeIgnoreCase(code).ifPresent(ex -> {
            if (id == null || !ex.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code 已存在");
            }
        });
        row.setCode(code);
        row.setName(name.trim());
        row.setType(StringUtils.hasText(str(body.get("type"))) ? str(body.get("type")).trim().toUpperCase() : TYPE_KB_CURATOR);
        row.setDescription(trim(str(body.get("description")), 512));
        if (body.get("enabled") instanceof Boolean b) row.setEnabled(b);
        row.setScheduleHour(clampInt(body.get("scheduleHour"), 0, 23, 3));
        row.setScheduleMinute(clampInt(body.get("scheduleMinute"), 0, 59, 0));
        Long modelId = longOrNull(body.get("systemModelId"));
        if (modelId != null) {
            systemModelRepository.findById(modelId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统模型不存在"));
        }
        row.setSystemModelId(modelId);
        Object cfg = body.get("config");
        if (cfg != null) {
            try {
                KbCuratorConfig parsed = objectMapper.convertValue(cfg, KbCuratorConfig.class);
                normalizeConfig(parsed);
                row.setConfigJson(objectMapper.writeValueAsString(parsed));
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "config 无效: " + e.getMessage());
            }
        } else if (id == null) {
            try {
                row.setConfigJson(objectMapper.writeValueAsString(new KbCuratorConfig()));
            } catch (Exception ignored) {
                row.setConfigJson("{}");
            }
        }
        return view(agentRepository.save(row));
    }

    @Transactional
    public void delete(Long id) {
        SystemAgent row = require(id);
        if (row.isRunning()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agent 正在运行，无法删除");
        }
        agentRepository.delete(row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pageRuns(Long agentId, int page, int size) {
        require(agentId);
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        var result = runRepository.findByAgentIdOrderByIdDesc(agentId, PageRequest.of(p, s));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", result.getContent().stream().map(this::runView).toList());
        m.put("page", p);
        m.put("size", s);
        m.put("total", result.getTotalElements());
        m.put("totalPages", result.getTotalPages());
        return m;
    }

    /** 调度 tick：每分钟检查是否到点。 */
    public void tickSchedules() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        for (SystemAgent agent : agentRepository.findByEnabledTrueAndType(TYPE_KB_CURATOR)) {
            if (agent.isRunning()) continue;
            if (!dueToday(agent, now)) continue;
            runAsync(agent.getId(), "SCHEDULE");
        }
    }

    public void runManual(Long id) {
        SystemAgent agent = require(id);
        if (!agent.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent 未启用");
        }
        if (agent.isRunning() || runningGuard.putIfAbsent(id, Boolean.TRUE) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agent 正在运行");
        }
        runningGuard.remove(id); // runAsync 自己会占坑
        runAsync(id, "MANUAL");
    }

    public void runAsync(Long agentId, String trigger) {
        if (runningGuard.putIfAbsent(agentId, Boolean.TRUE) != null) {
            log.info("[SystemAgent] skip {}, already guarded", agentId);
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                executeRun(agentId, trigger);
            } finally {
                runningGuard.remove(agentId);
            }
        });
    }

    private void executeRun(Long agentId, String trigger) {
        SystemAgent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) return;
        if (!TYPE_KB_CURATOR.equals(agent.getType())) {
            log.warn("[SystemAgent] unsupported type {}", agent.getType());
            return;
        }
        markRunning(agentId, true);
        SystemAgentRun run = new SystemAgentRun();
        run.setAgentId(agentId);
        run.setTriggerType(trigger);
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now(ZONE));
        run = runRepository.save(run);

        List<Map<String, Object>> details = new ArrayList<>();
        int ok = 0, fail = 0, skipped = 0;
        try {
            KbCuratorConfig cfg = readConfig(agent);
            SystemModel model = requireModel(agent.getSystemModelId());
            List<KnowledgeBase> candidates = pickCandidates(cfg);
            for (KnowledgeBase kb : candidates) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("knowledgeBaseId", kb.getId());
                item.put("name", kb.getName());
                try {
                    boolean changed = curateOne(agent, model, cfg, kb, item);
                    if (changed) ok++;
                    else skipped++;
                    item.put("status", changed ? "UPDATED" : "SKIPPED");
                } catch (Exception e) {
                    fail++;
                    item.put("status", "FAILED");
                    item.put("error", e.getMessage());
                    log.warn("[SystemAgent:{}] curate kb {} failed: {}", agent.getCode(), kb.getId(), e.getMessage());
                }
                details.add(item);
            }
            String status = fail == 0 ? "SUCCESS" : (ok > 0 ? "PARTIAL" : "FAILED");
            String summary = "更新 %d / 跳过 %d / 失败 %d（候选 %d）".formatted(ok, skipped, fail, candidates.size());
            finishRun(run.getId(), agentId, status, summary, details);
        } catch (Exception e) {
            log.error("[SystemAgent] run failed id={}: {}", agentId, e.getMessage(), e);
            finishRun(run.getId(), agentId, "FAILED", e.getMessage(), details);
        } finally {
            markRunning(agentId, false);
        }
    }

    private boolean curateOne(
            SystemAgent agent,
            SystemModel model,
            KbCuratorConfig cfg,
            KnowledgeBase kb,
            Map<String, Object> item
    ) {
        String content = kb.getContent() == null ? "" : kb.getContent();
        int chars = content.trim().length();
        LocalDateTime staleBefore = cfg.staleDays > 0
                ? LocalDateTime.now(ZONE).minusDays(cfg.staleDays)
                : null;
        boolean thin = chars < Math.max(0, cfg.thinContentChars);
        boolean stale = staleBefore != null && (kb.getUpdateTime() == null || kb.getUpdateTime().isBefore(staleBefore));
        if (!thin && !stale && cfg.staleDays > 0) {
            item.put("reason", "未到策展条件（内容够长且未过期）");
            return false;
        }

        String research = "";
        if (cfg.useTavily) {
            String apiKey = systemConfigService.getTavilyApiKey().orElse(null);
            if (!StringUtils.hasText(apiKey)) {
                item.put("tavily", "SKIPPED_NO_KEY");
            } else {
                List<String> queries = buildQueries(kb, Math.max(1, cfg.researchQueriesPerBase));
                StringBuilder sb = new StringBuilder();
                for (String q : queries) {
                    String r = tavilySearchService.search(
                            apiKey, q, Math.max(1, cfg.tavilyMaxResults),
                            cfg.tavilySearchDepth, "general");
                    sb.append("### 检索：").append(q).append("\n").append(r).append("\n\n");
                }
                research = sb.toString();
                item.put("tavilyQueries", queries);
            }
        }

        String system = """
                你是 AIMATEX 官方知识库的资深策展 Agent。目标：让条目更专业、可执行、结构清晰。
                要求：
                1. 保留原文正确事实；不确定的联网材料仅作补充，勿编造法规/价格/日期。
                2. 输出结构建议：概述与适用范围 → 标准/规范 → 操作清单 → 验收标准 → 反模式 → 示例。
                3. 使用简洁专业中文（若原文是英文可中英并列关键术语）。
                4. 只输出最终知识库正文 Markdown，不要前言后语。
                %s
                """.formatted(StringUtils.hasText(cfg.styleGuide) ? "额外风格：" + cfg.styleGuide.trim() : "");

        String user = """
                # 知识库元数据
                - 名称：%s
                - code：%s
                - 标签：%s
                - 摘要：%s

                # 现有正文
                %s

                # 联网检索材料（可能含噪声，请甄别）
                %s

                请产出完善后的正文。
                """.formatted(
                nullToEmpty(kb.getName()),
                nullToEmpty(kb.getCode()),
                nullToEmpty(kb.getTags()),
                nullToEmpty(kb.getSummary()),
                content.isBlank() ? "（空）" : content,
                research.isBlank() ? "（无）" : research
        );

        String rewritten = chatHelper.chatMessages(
                model,
                List.of(Message.system(system), Message.user(user)),
                cfg.temperature,
                Math.max(1024, cfg.maxTokens),
                true
        );
        if (!StringUtils.hasText(rewritten) || rewritten.length() < 40) {
            item.put("reason", "模型未返回有效正文");
            return false;
        }

        kb.setContent(rewritten);
        if (cfg.updateSummaryAndTags) {
            try {
                String meta = chatHelper.chatMessages(
                        model,
                        List.of(
                                Message.system("根据知识库正文生成一行摘要（≤120字）和逗号分隔标签（3-8个）。输出 JSON：{\"summary\":\"...\",\"tags\":\"a,b,c\"}，不要其它文字。"),
                                Message.user(rewritten.length() > 6000 ? rewritten.substring(0, 6000) : rewritten)
                        ),
                        0.2,
                        512,
                        true
                );
                JsonNode node = objectMapper.readTree(extractJson(meta));
                if (node.hasNonNull("summary")) {
                    kb.setSummary(trim(node.get("summary").asText(), 1024));
                }
                if (node.hasNonNull("tags")) {
                    kb.setTags(trim(node.get("tags").asText(), 512));
                }
            } catch (Exception e) {
                item.put("metaWarn", e.getMessage());
            }
        }
        knowledgeBaseRepository.save(kb);
        item.put("newChars", rewritten.length());
        return true;
    }

    private List<KnowledgeBase> pickCandidates(KbCuratorConfig cfg) {
        boolean emptyCats = cfg.categoryIds == null || cfg.categoryIds.isEmpty();
        List<Long> cats = emptyCats ? List.of(-1L) : cfg.categoryIds.stream().filter(Objects::nonNull).distinct().toList();
        int fetch = Math.max(cfg.maxBasesPerRun * 4, cfg.maxBasesPerRun);
        List<KnowledgeBase> all = knowledgeBaseRepository.findCuratorCandidates(
                emptyCats, cats, PageRequest.of(0, Math.min(200, Math.max(1, fetch))));
        LocalDateTime staleBefore = cfg.staleDays > 0
                ? LocalDateTime.now(ZONE).minusDays(cfg.staleDays)
                : null;
        List<KnowledgeBase> picked = new ArrayList<>();
        for (KnowledgeBase kb : all) {
            int chars = kb.getContent() == null ? 0 : kb.getContent().trim().length();
            boolean thin = chars < Math.max(0, cfg.thinContentChars);
            boolean stale = staleBefore == null
                    || kb.getUpdateTime() == null
                    || kb.getUpdateTime().isBefore(staleBefore);
            if (thin || stale) {
                picked.add(kb);
            }
            if (picked.size() >= Math.max(1, cfg.maxBasesPerRun)) break;
        }
        return picked;
    }

    private List<String> buildQueries(KnowledgeBase kb, int n) {
        List<String> qs = new ArrayList<>();
        qs.add(kb.getName() + " 最佳实践 规范");
        if (StringUtils.hasText(kb.getTags())) {
            qs.add(kb.getName() + " " + kb.getTags().split("[,，]")[0].trim() + " 指南");
        } else if (StringUtils.hasText(kb.getSummary())) {
            String s = kb.getSummary();
            qs.add((s.length() > 40 ? s.substring(0, 40) : s) + " 专业要点");
        } else {
            qs.add(kb.getName() + " checklist standards");
        }
        return qs.stream().limit(Math.max(1, n)).toList();
    }

    private boolean dueToday(SystemAgent agent, LocalDateTime now) {
        LocalTime scheduled = LocalTime.of(
                Math.min(23, Math.max(0, agent.getScheduleHour())),
                Math.min(59, Math.max(0, agent.getScheduleMinute()))
        );
        if (now.toLocalTime().isBefore(scheduled)) return false;
        LocalDateTime last = agent.getLastRunAt();
        if (last == null) return true;
        LocalDate today = now.toLocalDate();
        return last.atZone(ZONE).toLocalDate().isBefore(today);
    }

    @Transactional
    protected void markRunning(Long id, boolean running) {
        agentRepository.findById(id).ifPresent(a -> {
            a.setRunning(running);
            agentRepository.save(a);
        });
    }

    @Transactional
    protected void finishRun(Long runId, Long agentId, String status, String summary, List<Map<String, Object>> details) {
        runRepository.findById(runId).ifPresent(run -> {
            run.setStatus(status);
            run.setSummary(trim(summary, 1024));
            run.setFinishedAt(LocalDateTime.now(ZONE));
            try {
                run.setDetailJson(objectMapper.writeValueAsString(details));
            } catch (Exception e) {
                run.setDetailJson("[]");
            }
            runRepository.save(run);
        });
        agentRepository.findById(agentId).ifPresent(a -> {
            a.setLastRunAt(LocalDateTime.now(ZONE));
            a.setLastRunStatus(status);
            a.setLastRunSummary(trim(summary, 1024));
            a.setRunning(false);
            agentRepository.save(a);
        });
    }

    private SystemAgent require(Long id) {
        return agentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "系统 Agent 不存在"));
    }

    private SystemModel requireModel(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先为 Agent 选择系统模型");
        }
        return systemModelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统模型不存在"));
    }

    private KbCuratorConfig readConfig(SystemAgent agent) {
        try {
            if (!StringUtils.hasText(agent.getConfigJson())) return new KbCuratorConfig();
            KbCuratorConfig cfg = objectMapper.readValue(agent.getConfigJson(), KbCuratorConfig.class);
            normalizeConfig(cfg);
            return cfg;
        } catch (Exception e) {
            return new KbCuratorConfig();
        }
    }

    private void normalizeConfig(KbCuratorConfig cfg) {
        if (cfg.categoryIds == null) cfg.categoryIds = new ArrayList<>();
        cfg.maxBasesPerRun = Math.min(50, Math.max(1, cfg.maxBasesPerRun));
        cfg.thinContentChars = Math.min(20000, Math.max(0, cfg.thinContentChars));
        cfg.staleDays = Math.min(3650, Math.max(0, cfg.staleDays));
        cfg.tavilyMaxResults = Math.min(20, Math.max(1, cfg.tavilyMaxResults));
        cfg.researchQueriesPerBase = Math.min(5, Math.max(1, cfg.researchQueriesPerBase));
        cfg.maxTokens = Math.min(32000, Math.max(512, cfg.maxTokens));
        if (!StringUtils.hasText(cfg.tavilySearchDepth)) cfg.tavilySearchDepth = "basic";
    }

    private Map<String, Object> view(SystemAgent a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("type", a.getType());
        m.put("code", a.getCode());
        m.put("name", a.getName());
        m.put("description", a.getDescription());
        m.put("enabled", a.isEnabled());
        m.put("scheduleHour", a.getScheduleHour());
        m.put("scheduleMinute", a.getScheduleMinute());
        m.put("systemModelId", a.getSystemModelId());
        m.put("running", a.isRunning());
        m.put("lastRunAt", a.getLastRunAt() == null ? null : a.getLastRunAt().toString());
        m.put("lastRunStatus", a.getLastRunStatus());
        m.put("lastRunSummary", a.getLastRunSummary());
        m.put("tavilyConfigured", systemConfigService.getTavilyApiKey().isPresent());
        try {
            m.put("config", readConfig(a));
        } catch (Exception e) {
            m.put("config", new KbCuratorConfig());
        }
        m.put("updateTime", a.getUpdateTime() == null ? null : a.getUpdateTime().toString());
        return m;
    }

    private Map<String, Object> runView(SystemAgentRun r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("agentId", r.getAgentId());
        m.put("triggerType", r.getTriggerType());
        m.put("status", r.getStatus());
        m.put("startedAt", r.getStartedAt() == null ? null : r.getStartedAt().toString());
        m.put("finishedAt", r.getFinishedAt() == null ? null : r.getFinishedAt().toString());
        m.put("summary", r.getSummary());
        try {
            m.put("detail", StringUtils.hasText(r.getDetailJson())
                    ? objectMapper.readValue(r.getDetailJson(), Object.class) : List.of());
        } catch (Exception e) {
            m.put("detail", List.of());
        }
        return m;
    }

    private static String extractJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        int i = s.indexOf('{');
        int j = s.lastIndexOf('}');
        if (i >= 0 && j > i) return s.substring(i, j + 1);
        return s;
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static String trim(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
    private static int clampInt(Object o, int min, int max, int def) {
        if (o instanceof Number n) return Math.min(max, Math.max(min, n.intValue()));
        if (o != null) {
            try { return Math.min(max, Math.max(min, Integer.parseInt(String.valueOf(o).trim()))); }
            catch (Exception ignored) {}
        }
        return def;
    }
    private static Long longOrNull(Object o) {
        if (o == null || "".equals(o)) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }
}
