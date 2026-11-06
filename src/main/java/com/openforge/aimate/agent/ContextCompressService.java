package com.openforge.aimate.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.agent.compress.CompressPlan;
import com.openforge.aimate.agent.compress.CompressPolicyV1;
import com.openforge.aimate.agent.compress.IntelligentCompressArch;
import com.openforge.aimate.agent.dto.ContextCompressResultDto;
import com.openforge.aimate.agent.dto.ContextUsageDto;
import com.openforge.aimate.agent.dto.SessionState;
import com.openforge.aimate.config.SystemConfigService;
import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.domain.SessionMessage;
import com.openforge.aimate.llm.LlmRouter;
import com.openforge.aimate.llm.model.ChatRequest;
import com.openforge.aimate.llm.model.ChatResponse;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.memory.SoulMemoryBridge;
import com.openforge.aimate.repository.AgentSessionRepository;
import com.openforge.aimate.repository.SessionMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话上下文压缩管线 — {@link IntelligentCompressArch}（智能压缩 / ICA）。
 * <ul>
 *   <li>L0 确定性裁剪（摘要用稿）</li>
 *   <li>L1 结构化 SessionState JSON 滚动合并</li>
 *   <li>L2 write-before-compact → Soul（记忆开启时）</li>
 *   <li>L3 exclude 老化消息；pin 永不 exclude；近期原文保留</li>
 *   <li>P  多因素策略（占用压力 / 可折段落 / 对话占比 / 自适应保留窗）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextCompressService {

    /** 尽量覆盖的完整 tool 轮次数（与压力自适应 keepFloor 叠加） */
    private static final int KEEP_TOOL_ROUNDS = 3;
    private static final int TRANSCRIPT_MAX_CHARS = 28_000;
    private static final int SUMMARY_MAX_CHARS = 6_000;

    private static final String SUMMARIZER_SYSTEM = """
            You maintain a structured session state JSON for a long-running AI agent.
            Output ONLY a single JSON object (no markdown fences) with exactly these keys:
            intent (string|null), goals (string[]), decisions (string[]), constraints (string[]),
            entities (string[]), openTasks (string[]), toolOutcomes (string[]), nextSteps (string[]).
            Merge prior state with new turns. Prefer short factual bullets.
            entities: ids, file paths, URLs, names — NEVER paste code bodies; path+symbol refs only.
            constraints/decisions: keep user-confirmed rules and choices verbatim when short.
            Drop chitchat, repeated failures, verbose dumps. Same language as the conversation.
            Empty sections must be [] or null — do not invent facts.
            """;

    private final SessionMessageRepository messageRepository;
    private final AgentSessionRepository sessionRepository;
    private final ContextUsageService contextUsageService;
    private final ContextPruneService contextPruneService;
    private final LlmRouter llmRouter;
    private final ObjectMapper objectMapper;
    private final SoulMemoryBridge soulMemoryBridge;
    private final SystemConfigService systemConfigService;
    private final UserToolSettingsService userToolSettingsService;

    @Transactional
    public ContextCompressResultDto compress(AgentSession session, Long systemModelId) {
        session = sessionRepository.findById(session.getId())
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));

        ContextUsageDto before = contextUsageService.estimateRaw(session, systemModelId);

        List<SessionMessage> all = messageRepository.findByAgentSession_IdOrderBySeqAsc(session.getId());
        List<SessionMessage> active = all.stream()
                .filter(m -> !m.isExcludeFromContext())
                .toList();

        List<SessionMessage> rest = new ArrayList<>();
        for (SessionMessage m : active) {
            if (!"system".equalsIgnoreCase(m.getRole())) {
                rest.add(m);
            }
        }

        CompressPlan plan = IntelligentCompressArch.activePolicy().analyze(rest, before, KEEP_TOOL_ROUNDS);
        before = before.withCompressPolicy(plan.recommended(), plan.available(), plan.urgency(), plan.hint());

        if (!plan.available()) {
            return ContextCompressResultDto.of(
                    false, 0, rest.size(), 0, before, before,
                    plan.hint() != null ? plan.hint() : "暂不可压缩"
            );
        }

        int keepCount = Math.min(rest.size(), Math.max(1, plan.keepRecent()));
        List<SessionMessage> recent = keepCount >= rest.size()
                ? new ArrayList<>(rest)
                : new ArrayList<>(rest.subList(rest.size() - keepCount, rest.size()));
        List<SessionMessage> old = keepCount >= rest.size()
                ? new ArrayList<>()
                : new ArrayList<>(rest.subList(0, rest.size() - keepCount));

        // 避免切断 tool 链
        int adjust = 0;
        while (!old.isEmpty() && !recent.isEmpty() && adjust < 8) {
            SessionMessage head = recent.get(0);
            if ("tool".equalsIgnoreCase(head.getRole())
                    || (head.getReplyToMessageId() != null)
                    || ("assistant".equalsIgnoreCase(head.getRole())
                        && (head.getContent() == null || head.getContent().isBlank())
                        && head.getToolCallsJson() != null)) {
                SessionMessage moved = old.remove(old.size() - 1);
                recent.add(0, moved);
                adjust++;
            } else {
                break;
            }
        }

        // pin：从 old 抽回，永不 exclude
        List<SessionMessage> pinnedKept = new ArrayList<>();
        old.removeIf(m -> {
            if (m.isPinned()) {
                pinnedKept.add(m);
                return true;
            }
            return false;
        });

        int minFold = plan.minFoldable();
        if (old.size() < minFold) {
            String hint = plan.hint() != null ? plan.hint() : "可压缩段落不足（含钉住消息），请稍后再试";
            return ContextCompressResultDto.of(
                    false, 0, recent.size() + pinnedKept.size(), 0, before, before, hint
            );
        }

        SessionState prior = SessionState.parse(session.getContextSummary(), objectMapper);
        String transcript = buildTranscript(prior, old);
        SessionState next = mergeStructuredState(prior, transcript);

        String summaryJson = next.toJson(objectMapper);
        if (summaryJson.length() > SUMMARY_MAX_CHARS) {
            summaryJson = summaryJson.substring(0, SUMMARY_MAX_CHARS);
        }

        // L2 write-before-compact
        writeDurableFactsToSoul(session.getUserId(), session.getUserAgentId(), prior, next);

        for (SessionMessage m : old) {
            m.setExcludeFromContext(true);
            messageRepository.save(m);
        }
        session.setContextSummary(summaryJson);
        sessionRepository.save(session);

        session = sessionRepository.findById(session.getId()).orElse(session);
        ContextUsageDto after = contextUsageService.estimate(session, systemModelId);

        log.info("[ContextCompress] session={} compressed={} kept={} pinned={} entities={} constraints={} before={} after={}",
                session.getSessionId(), old.size(), recent.size(), pinnedKept.size(),
                next.entityCount(), next.constraintCount(),
                before.usedTokens(), after.usedTokens());

        return ContextCompressResultDto.of(
                true,
                old.size(),
                recent.size() + pinnedKept.size(),
                summaryJson.length(),
                before,
                after,
                IntelligentCompressArch.marketingLineZh()
                        + "：已折为结构化会话状态；钉住与近期原文保留；聊天记录仍可回看"
        );
    }

    /** @deprecated use {@link CompressPolicyV1#computeKeepRecentCount} */
    static int computeKeepRecentCount(List<SessionMessage> rest) {
        return CompressPolicyV1.computeKeepRecentCount(
                rest, CompressPolicyV1.keepFloor(CompressPlan.PressureBand.NORMAL), KEEP_TOOL_ROUNDS);
    }

    /**
     * 为占用估算补全压缩策略字段（只读分析，不改库）。
     */
    @Transactional(readOnly = true)
    public ContextUsageDto enrichUsage(AgentSession session, ContextUsageDto raw) {
        if (raw == null) return null;
        List<SessionMessage> all = messageRepository.findByAgentSession_IdOrderBySeqAsc(session.getId());
        List<SessionMessage> rest = new ArrayList<>();
        for (SessionMessage m : all) {
            if (m.isExcludeFromContext()) continue;
            if ("system".equalsIgnoreCase(m.getRole())) continue;
            rest.add(m);
        }
        CompressPlan plan = IntelligentCompressArch.activePolicy().analyze(rest, raw, KEEP_TOOL_ROUNDS);
        return raw.withCompressPolicy(plan.recommended(), plan.available(), plan.urgency(), plan.hint());
    }

    private String buildTranscript(SessionState prior, List<SessionMessage> old) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Prior session state (JSON)\n");
        sb.append(prior.toJson(objectMapper)).append("\n\n");
        sb.append("### Turns to fold (pruned)\n");
        List<String> lines = contextPruneService.pruneForSummarizer(old);
        for (String line : lines) {
            sb.append(line).append('\n');
            if (sb.length() >= TRANSCRIPT_MAX_CHARS) {
                sb.append("\n…(transcript truncated)\n");
                break;
            }
        }
        return sb.toString();
    }

    /**
     * 群聊等复用：同一套结构化 SessionState 摘要合并（L1）。
     */
    public SessionState mergeStructuredState(SessionState prior, String transcript) {
        SessionState next = callStructuredSummarizer(prior, transcript);
        if (contextPruneService.transcriptHasPathsOrUrls(transcript) && next.entityCount() == 0) {
            next = callStructuredSummarizer(prior, transcript + "\n\nNOTE: Extract all file paths and URLs into entities.");
        }
        return next;
    }

    /** 群聊等复用：压缩前把新增耐久事实写入 Soul（L2）。 */
    public void writeDurableFactsToSoul(Long userId, Long userAgentId, SessionState prior, SessionState next) {
        if (userId == null || !soulMemoryBridge.isEnabled() || !systemConfigService.isRagMemoryEnabled()) {
            return;
        }
        if (!userToolSettingsService.isMemoryEnabled(userId, userAgentId)) {
            return;
        }
        try {
            storeNewItems(next.decisions(), prior.decisions(), "DECISION", 0.85f, userId, userAgentId);
            storeNewItems(next.constraints(), prior.constraints(), "PREFERENCE", 0.8f, userId, userAgentId);
            storeNewItems(next.entities(), prior.entities(), "FACT", 0.65f, userId, userAgentId);
            if (StringUtils.hasText(next.intent())
                    && (prior.intent() == null || !next.intent().equals(prior.intent()))) {
                soulMemoryBridge.store("Session intent: " + next.intent(), "FACT", 0.7f, userId, userAgentId);
            }
        } catch (Exception e) {
            log.warn("[ContextCompress] Soul write-before-compact failed: {}", e.getMessage());
        }
    }

    private SessionState callStructuredSummarizer(SessionState prior, String transcript) {
        ChatResponse response = llmRouter.chat(ChatRequest.simple(null, List.of(
                Message.system(SUMMARIZER_SYSTEM),
                Message.user("Merge into updated session state JSON:\n\n" + transcript)
        )));
        Message msg = response.firstMessage();
        String raw = msg != null ? msg.content() : null;
        if (!StringUtils.hasText(raw)) {
            throw new IllegalStateException("摘要模型未返回内容，压缩失败");
        }
        String cleaned = stripFences(raw.trim());
        SessionState parsed = SessionState.parse(cleaned, objectMapper);
        // 若完全解析失败成「整段塞 intent」且像散文，仍接受但记日志
        if (parsed.intent() != null && parsed.intent().length() > 400
                && parsed.entityCount() == 0 && parsed.constraintCount() == 0
                && (parsed.goals() == null || parsed.goals().isEmpty())) {
            log.warn("[ContextCompress] summarizer returned prose-like payload; stored as intent fallback");
        }
        return parsed;
    }

    private static String stripFences(String s) {
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            int last = s.lastIndexOf("```");
            if (firstNl > 0 && last > firstNl) {
                return s.substring(firstNl + 1, last).trim();
            }
        }
        return s;
    }

    private void storeNewItems(List<String> next, List<String> prior, String kind, float importance,
                               Long userId, Long userAgentId) {
        if (next == null || next.isEmpty()) return;
        List<String> old = prior != null ? prior : List.of();
        for (String item : next) {
            if (!StringUtils.hasText(item)) continue;
            String t = item.trim();
            boolean exists = old.stream().anyMatch(p -> p != null && p.trim().equalsIgnoreCase(t));
            if (!exists) {
                soulMemoryBridge.store(t, kind, importance, userId, userAgentId);
            }
        }
    }

    /** 占用压力达阈值时建议压缩（最终以 enrichUsage / Plan.recommended 为准）。 */
    public boolean shouldSuggestCompress(ContextUsageDto usage, AgentSession session) {
        return usage != null && usage.compressRecommended();
    }
}
