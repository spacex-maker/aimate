package com.openforge.aimate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openforge.aimate.config.SystemConfigService;
import com.openforge.aimate.domain.AgentPromptVersion;
import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.domain.SessionMessage;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.domain.UserProfile;
import com.openforge.aimate.repository.AgentPromptVersionRepository;
import com.openforge.aimate.repository.AgentSessionRepository;
import com.openforge.aimate.repository.SessionMessageRepository;
import com.openforge.aimate.repository.SystemModelRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPromptService {

    public static final String CFG_PROMPT_MODEL_ID = "CHAT_AGENT_PROMPT_SYSTEM_MODEL_ID";

    private final SystemConfigService systemConfigService;
    private final SystemModelRepository systemModelRepository;
    private final AgentPromptVersionRepository promptVersionRepository;
    private final UserAgentRepository userAgentRepository;
    private final AgentSessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final SystemModelChatHelper systemModelChatHelper;

    @Transactional
    public AgentPromptVersion generateInitialPrompt(UserAgent agent, UserProfile profile) {
        String prompt = callLlmForPrompt(
                """
                你是提示词工程师。根据用户画像，生成一段中文「私人秘书」系统提示词（system prompt）。
                要求：贴合用户职业与生活场景；明确性格、说话风格、工作边界；不要输出解释；直接输出提示词正文。
                """,
                "用户画像：\n职业=" + nullToEmpty(profile.getOccupation())
                        + "\n年龄=" + profile.getAge()
                        + "\n性别=" + nullToEmpty(profile.getGender())
                        + "\n简介=" + nullToEmpty(profile.getBio())
                        + "\n秘书名称=" + agent.getName()
        );
        if (!StringUtils.hasText(prompt)) {
            prompt = defaultSecretaryPrompt(profile, agent.getName());
        }
        ArrayNode atoms = objectMapper.createArrayNode();
        ObjectNode atom = atoms.addObject();
        atom.put("id", UUID.randomUUID().toString());
        atom.put("text", "根据用户画像生成的初始人设");
        atom.put("excluded", false);
        return saveNewVersion(agent, prompt.trim(), "初始版本：根据用户画像生成", atoms.toString(), true);
    }

    @Transactional
    public AgentPromptVersion dailyOptimize(UserAgent agent) {
        AgentPromptVersion current = promptVersionRepository.findFirstByUserAgentIdAndActiveTrue(agent.getId())
                .orElse(null);
        String currentText = current != null ? current.getPromptText() : defaultSecretaryPrompt(null, agent.getName());
        String recent = collectRecentContext(agent.getUserId(), agent.getId());
        String raw = callLlmForPrompt(
                """
                你是秘书人设迭代器。基于当前系统提示词与近期对话摘要，产出 JSON：
                {"summary":"本版变更摘要","atoms":[{"id":"uuid","text":"原子更新说明"}],"prompt":"完整新系统提示词"}
                只输出 JSON。
                """,
                "当前提示词：\n" + currentText + "\n\n近期上下文：\n" + recent
        );

        String summary = "日常优化";
        ArrayNode atoms = objectMapper.createArrayNode();
        String prompt = currentText;
        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            summary = node.path("summary").asText(summary);
            prompt = node.path("prompt").asText(prompt);
            JsonNode arr = node.path("atoms");
            if (arr.isArray()) {
                for (JsonNode a : arr) {
                    ObjectNode o = atoms.addObject();
                    o.put("id", a.path("id").asText(UUID.randomUUID().toString()));
                    o.put("text", a.path("text").asText(""));
                    o.put("excluded", false);
                }
            }
        } catch (Exception e) {
            log.warn("[AgentPrompt] parse daily optimize failed: {}", e.toString());
            if (StringUtils.hasText(raw) && !raw.equals(currentText)) {
                prompt = raw.trim();
            }
            ObjectNode o = atoms.addObject();
            o.put("id", UUID.randomUUID().toString());
            o.put("text", "自动优化一轮");
            o.put("excluded", false);
        }
        if (atoms.isEmpty()) {
            ObjectNode o = atoms.addObject();
            o.put("id", UUID.randomUUID().toString());
            o.put("text", summary);
            o.put("excluded", false);
        }
        return saveNewVersion(agent, prompt, summary, atoms.toString(), true);
    }

    @Transactional
    public AgentPromptVersion excludeAtoms(Long userId, Long userAgentId, List<String> excludeIds) {
        UserAgent agent = userAgentRepository.findByIdAndUserId(userAgentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 不存在"));
        AgentPromptVersion current = promptVersionRepository.findFirstByUserAgentIdAndActiveTrue(agent.getId())
                .orElseThrow(() -> new IllegalStateException("暂无提示词版本"));
        try {
            ArrayNode atoms = (ArrayNode) objectMapper.readTree(
                    StringUtils.hasText(current.getAtomsJson()) ? current.getAtomsJson() : "[]");
            for (JsonNode n : atoms) {
                if (n instanceof ObjectNode on && excludeIds != null
                        && excludeIds.contains(on.path("id").asText())) {
                    on.put("excluded", true);
                }
            }
            StringBuilder kept = new StringBuilder();
            for (JsonNode n : atoms) {
                if (!n.path("excluded").asBoolean(false)) {
                    if (kept.length() > 0) kept.append('\n');
                    kept.append("- ").append(n.path("text").asText());
                }
            }
            String newPrompt = callLlmForPrompt(
                    "根据原提示词与保留的原子更新，输出一份完整系统提示词正文，不要解释。",
                    "原提示词：\n" + current.getPromptText()
                            + "\n\n保留的原子更新：\n" + (kept.length() == 0 ? "（无）" : kept)
            );
            if (!StringUtils.hasText(newPrompt)) {
                newPrompt = current.getPromptText();
            }
            return saveNewVersion(
                    agent,
                    newPrompt.trim(),
                    "用户微调：剔除部分原子更新",
                    objectMapper.writeValueAsString(atoms),
                    true
            );
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("微调失败: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public String activePersonaPrompt(Long userAgentId) {
        return promptVersionRepository.findFirstByUserAgentIdAndActiveTrue(userAgentId)
                .map(AgentPromptVersion::getPromptText)
                .orElse("");
    }

    private AgentPromptVersion saveNewVersion(
            UserAgent agent,
            String prompt,
            String summary,
            String atomsJson,
            boolean activate
    ) {
        int next = agent.getCurrentPromptVersion() + 1;
        if (activate) {
            for (AgentPromptVersion v : promptVersionRepository.findByUserAgentIdOrderByVersionNoDesc(agent.getId())) {
                if (v.isActive()) {
                    v.setActive(false);
                    promptVersionRepository.save(v);
                }
            }
        }
        AgentPromptVersion ver = new AgentPromptVersion();
        ver.setUserAgentId(agent.getId());
        ver.setVersionNo(next);
        ver.setPromptText(prompt);
        ver.setChangeSummary(summary);
        ver.setAtomsJson(atomsJson);
        ver.setActive(activate);
        ver = promptVersionRepository.save(ver);
        agent.setCurrentPromptVersion(next);
        if (agent.getStatus() == UserAgent.Status.PENDING_PROFILE) {
            agent.setStatus(UserAgent.Status.ACTIVE);
        }
        userAgentRepository.save(agent);
        return ver;
    }

    private String callLlmForPrompt(String system, String user) {
        SystemModel model = resolvePromptModel();
        if (model == null) return "";
        try {
            return systemModelChatHelper.chatText(model, system, user);
        } catch (Exception e) {
            log.warn("[AgentPrompt] LLM failed: {}", e.toString());
            return "";
        }
    }

    private SystemModel resolvePromptModel() {
        Long id = systemConfigService.get(CFG_PROMPT_MODEL_ID)
                .map(s -> {
                    try { return Long.parseLong(s.trim()); }
                    catch (Exception e) { return null; }
                })
                .orElse(null);
        if (id != null) {
            return systemModelRepository.findById(id).filter(SystemModel::isEnabled).orElse(null);
        }
        return systemModelRepository.findAll().stream()
                .filter(SystemModel::isEnabled)
                .filter(m -> m.getProvider() != null && m.getProvider().toLowerCase().contains("deepseek"))
                .sorted((a, b) -> {
                    String da = (a.getDisplayName() + a.getModelId()).toLowerCase();
                    String db = (b.getDisplayName() + b.getModelId()).toLowerCase();
                    int sa = (da.contains("pro") || da.contains("reasoner") ? 0 : 1);
                    int sb = (db.contains("pro") || db.contains("reasoner") ? 0 : 1);
                    return Integer.compare(sa, sb);
                })
                .findFirst()
                .orElseGet(() -> systemModelRepository.findAll().stream()
                        .filter(SystemModel::isEnabled)
                        .findFirst()
                        .orElse(null));
    }

    private String collectRecentContext(Long userId, Long userAgentId) {
        try {
            List<AgentSession> sessions = sessionRepository
                    .findByUserIdAndUserAgentIdAndHiddenFalseOrderByUpdateTimeDesc(
                            userId, userAgentId, PageRequest.of(0, 3));
            if (sessions == null || sessions.isEmpty()) {
                sessions = sessionRepository.findByUserIdAndHiddenFalseOrderByCreateTimeDesc(
                        userId, PageRequest.of(0, 3));
            }
            if (sessions == null || sessions.isEmpty()) return "（暂无近期对话）";
            StringBuilder sb = new StringBuilder();
            for (AgentSession s : sessions) {
                List<SessionMessage> msgs = messageRepository.findByAgentSession_IdOrderBySeqAsc(s.getId());
                for (SessionMessage m : msgs) {
                    if (!"user".equals(m.getRole()) && !"assistant".equals(m.getRole())) continue;
                    String c = m.getContent();
                    if (!StringUtils.hasText(c)) continue;
                    if (c.length() > 200) c = c.substring(0, 200) + "…";
                    sb.append(m.getRole()).append(": ").append(c).append('\n');
                }
            }
            return sb.length() == 0 ? "（暂无近期对话）" : sb.toString();
        } catch (Exception e) {
            return "（暂无近期对话）";
        }
    }

    private static String defaultSecretaryPrompt(UserProfile profile, String name) {
        String occ = profile != null ? nullToEmpty(profile.getOccupation()) : "";
        return "你是「" + name + "」，用户的专属秘书。"
                + (StringUtils.hasText(occ) ? "用户职业是" + occ + "。" : "")
                + "请以专业、体贴、高效的方式协助用户处理工作与生活事务；回答简洁有用；不确定时主动澄清。";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String extractJson(String raw) {
        if (raw == null) return "{}";
        String t = raw.trim();
        int i = t.indexOf('{');
        int j = t.lastIndexOf('}');
        if (i >= 0 && j > i) return t.substring(i, j + 1);
        return t;
    }
}
