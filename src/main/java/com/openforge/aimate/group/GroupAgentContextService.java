package com.openforge.aimate.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.agent.AgentPromptService;
import com.openforge.aimate.agent.ContextCompressService;
import com.openforge.aimate.agent.ContextUsageService;
import com.openforge.aimate.agent.dto.ContextCompressResultDto;
import com.openforge.aimate.agent.dto.ContextUsageDto;
import com.openforge.aimate.agent.dto.SessionState;
import com.openforge.aimate.domain.ChatGroup;
import com.openforge.aimate.domain.GroupAgentContext;
import com.openforge.aimate.domain.GroupMember;
import com.openforge.aimate.domain.GroupMessage;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.llm.TokenCounter;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.repository.GroupAgentContextRepository;
import com.openforge.aimate.repository.GroupMemberRepository;
import com.openforge.aimate.repository.GroupMessageRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import com.openforge.aimate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * 群聊中每个 Agent 独立的上下文占用估算与压缩（与单聊 SessionState 管线同方案）。
 * <ul>
 *   <li>水位 {@code foldedThroughMessageId}：更早消息已折进摘要</li>
 *   <li>摘要字段同 {@code agent_sessions.context_summary}</li>
 *   <li>群消息不删，仅影响该 Agent 送模窗口</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupAgentContextService {

    /** 与 ContextCompressService.KEEP_RECENT_MIN 对齐 */
    private static final int KEEP_RECENT_MIN = 14;
    private static final int MIN_COMPRESSIBLE = 6;
    private static final int SUMMARY_MAX_CHARS = 6_000;
    private static final int TRANSCRIPT_MAX_CHARS = 28_000;
    /** 扫描上限，避免一次拉全表 */
    private static final int SCAN_LIMIT = 400;

    private final GroupAgentContextRepository contextRepository;
    private final GroupMessageRepository messageRepository;
    private final GroupMemberRepository memberRepository;
    private final UserAgentRepository userAgentRepository;
    private final UserRepository userRepository;
    private final GroupAccessService access;
    private final ContextUsageService contextUsageService;
    private final ContextCompressService contextCompressService;
    private final AgentPromptService agentPromptService;
    private final GroupConversationViewService conversationViewService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> listUsage(Long userId, String publicId, Long systemModelId) {
        ChatGroup g = access.requireActiveGroup(publicId);
        access.requireUserMember(g, userId);
        List<GroupMember> agents = memberRepository.findByGroupIdAndMemberType(
                g.getId(), GroupMember.MemberType.AGENT);
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (GroupMember am : agents) {
            if (am.getUserAgentId() == null) continue;
            ContextUsageDto usage = estimateForMember(g, am, systemModelId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userAgentId", am.getUserAgentId());
            row.put("memberId", am.getId());
            row.put("usage", usage);
            items.add(row);
        }
        out.put("items", items);
        return out;
    }

    @Transactional(readOnly = true)
    public ContextUsageDto getUsage(Long userId, String publicId, Long userAgentId, Long systemModelId) {
        ChatGroup g = access.requireActiveGroup(publicId);
        access.requireUserMember(g, userId);
        GroupMember am = requireAgentMember(g.getId(), userAgentId);
        return estimateForMember(g, am, systemModelId);
    }

    @Transactional
    public ContextCompressResultDto compress(Long userId, String publicId, Long userAgentId, Long systemModelId) {
        ChatGroup g = access.requireActiveGroup(publicId);
        access.requireUserMember(g, userId);
        GroupMember am = requireAgentMember(g.getId(), userAgentId);
        UserAgent ua = userAgentRepository.findById(userAgentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent 不存在"));

        GroupAgentContext ctx = getOrCreate(g.getId(), userAgentId);
        ContextUsageDto before = estimateForMember(g, am, systemModelId);

        List<GroupMessage> active = loadActiveChatMessages(g.getId(), ctx.getFoldedThroughMessageId());
        if (active.size() <= KEEP_RECENT_MIN + MIN_COMPRESSIBLE) {
            return new ContextCompressResultDto(
                    false, 0, active.size(), 0, before, before,
                    "对话轮次尚少，暂无需压缩（建议对话更长后再试）"
            );
        }

        int keepCount = Math.min(active.size(), KEEP_RECENT_MIN);
        List<GroupMessage> recent = new ArrayList<>(active.subList(active.size() - keepCount, active.size()));
        List<GroupMessage> old = new ArrayList<>(active.subList(0, active.size() - keepCount));
        if (old.size() < MIN_COMPRESSIBLE) {
            return new ContextCompressResultDto(
                    false, 0, recent.size(), 0, before, before,
                    "可压缩段落不足，请稍后再试"
            );
        }

        SessionState prior = SessionState.parse(ctx.getContextSummary(), objectMapper);
        String transcript = buildTranscript(prior, old, ua);
        SessionState next = contextCompressService.mergeStructuredState(prior, transcript);
        String summaryJson = next.toJson(objectMapper);
        if (summaryJson.length() > SUMMARY_MAX_CHARS) {
            summaryJson = summaryJson.substring(0, SUMMARY_MAX_CHARS);
        }

        contextCompressService.writeDurableFactsToSoul(ua.getUserId(), ua.getId(), prior, next);

        Long foldThrough = old.get(old.size() - 1).getId();
        ctx.setContextSummary(summaryJson);
        ctx.setFoldedThroughMessageId(foldThrough);
        contextRepository.save(ctx);

        ContextUsageDto after = estimateForMember(g, am, systemModelId);
        log.info("[GroupAgentContext] compress group={} agent={} foldedThrough={} before={} after={}",
                g.getPublicId(), ua.getName(), foldThrough, before.usedTokens(), after.usedTokens());

        return new ContextCompressResultDto(
                true,
                old.size(),
                recent.size(),
                summaryJson.length(),
                before,
                after,
                "已压缩为结构化会话状态；近期原文保留；群聊记录仍可回看"
        );
    }

    /**
     * 供派发送模：系统提示之后插入 SessionState，再跟近期群聊行。
     * @return messages 供 LLM（含 system）；调用方可再追加最新触发句
     */
    @Transactional(readOnly = true)
    public List<Message> buildLlmMessages(
            ChatGroup group,
            UserAgent ua,
            String systemPrompt,
            GroupMessage latestTrigger
    ) {
        GroupAgentContext ctx = contextRepository
                .findByGroupIdAndUserAgentId(group.getId(), ua.getId())
                .orElse(null);
        Long folded = ctx != null ? ctx.getFoldedThroughMessageId() : null;
        String summary = ctx != null ? ctx.getContextSummary() : null;

        List<Message> msgs = new ArrayList<>();
        msgs.add(Message.system(systemPrompt));
        if (StringUtils.hasText(summary)) {
            SessionState state = SessionState.parse(summary, objectMapper);
            msgs.add(Message.system(state.toPromptBlock(objectMapper)));
        }

        // 人类可见结构化会话：时间/@ /引用/已读未读/系统事件
        List<Map<String, Object>> structured = conversationViewService.buildStructuredMessages(group.getId(), 40);
        if (folded != null) {
            structured = structured.stream()
                    .filter(row -> {
                        Object id = row.get("id");
                        return !(id instanceof Number n) || n.longValue() > folded;
                    })
                    .toList();
        }
        String transcript = conversationViewService.renderTranscript(structured, 6000);
        if (StringUtils.hasText(transcript)) {
            msgs.add(Message.user(transcript));
        }
        if (latestTrigger != null) {
            List<Map<String, Object>> one = conversationViewService.buildStructuredMessages(group.getId(), 8);
            Map<String, Object> hit = one.stream()
                    .filter(r -> latestTrigger.getId() != null && latestTrigger.getId().equals(r.get("id")))
                    .findFirst()
                    .orElse(null);
            if (hit != null) {
                msgs.add(Message.user("【本轮要回应的最新消息】\n" + conversationViewService.renderTranscript(List.of(hit), 2000)));
            } else {
                msgs.add(Message.user("【本轮要回应的最新消息】\n" + formatLine(latestTrigger)));
            }
        }
        return msgs;
    }

    /** 投票 / Local Bridge 用的近期对话文本（结构化人类可见视图）。 */
    @Transactional(readOnly = true)
    public String buildDialogueText(ChatGroup group, Long userAgentId, GroupMessage latest) {
        GroupAgentContext ctx = contextRepository
                .findByGroupIdAndUserAgentId(group.getId(), userAgentId)
                .orElse(null);
        Long folded = ctx != null ? ctx.getFoldedThroughMessageId() : null;
        StringBuilder sb = new StringBuilder();
        if (ctx != null && StringUtils.hasText(ctx.getContextSummary())) {
            SessionState state = SessionState.parse(ctx.getContextSummary(), objectMapper);
            sb.append(state.toPromptBlock(objectMapper)).append('\n');
        }
        List<Map<String, Object>> structured = conversationViewService.buildStructuredMessages(group.getId(), 28);
        if (folded != null) {
            structured = structured.stream()
                    .filter(row -> {
                        Object id = row.get("id");
                        return !(id instanceof Number n) || n.longValue() > folded;
                    })
                    .toList();
        }
        sb.append(conversationViewService.renderTranscript(structured, 4500));
        if (latest != null && structured.stream().noneMatch(r -> latest.getId() != null && latest.getId().equals(r.get("id")))) {
            sb.append('\n').append(formatLine(latest));
        }
        String joined = sb.toString().trim();
        if (joined.length() > 4500) {
            return joined.substring(joined.length() - 4500);
        }
        return joined;
    }

    private ContextUsageDto estimateForMember(ChatGroup g, GroupMember am, Long systemModelId) {
        UserAgent ua = userAgentRepository.findById(am.getUserAgentId()).orElse(null);
        if (ua == null) {
            return ContextUsageDto.of(0, 0, 0, ContextUsageService.DEFAULT_LIMIT, false);
        }
        Long modelId = systemModelId != null ? systemModelId : ua.getDefaultSystemModelId();
        int limit = contextUsageService.resolveLimit(modelId);

        GroupAgentContext ctx = contextRepository
                .findByGroupIdAndUserAgentId(g.getId(), ua.getId())
                .orElse(null);
        Long folded = ctx != null ? ctx.getFoldedThroughMessageId() : null;

        String persona = safePersona(ua.getId());
        String systemApprox = "群聊同事「" + ua.getName() + "」\n" + persona;
        int systemTokens = TokenCounter.estimateTokens(systemApprox);

        int conversationTokens = 0;
        if (ctx != null && StringUtils.hasText(ctx.getContextSummary())) {
            conversationTokens += TokenCounter.estimateTokens(ctx.getContextSummary());
        }
        List<Map<String, Object>> structured = conversationViewService.buildStructuredMessages(g.getId(), 40);
        if (folded != null) {
            structured = structured.stream()
                    .filter(row -> {
                        Object id = row.get("id");
                        return !(id instanceof Number n) || n.longValue() > folded;
                    })
                    .toList();
        }
        conversationTokens += TokenCounter.estimateTokens(
                conversationViewService.renderTranscript(structured, 8000));

        // 群聊 CLOUD_LLM 无工具定义；Local Bridge 也不走平台 tools schema
        int toolsTokens = 0;
        return ContextUsageDto.of(systemTokens, conversationTokens, toolsTokens, limit);
    }

    private List<GroupMessage> loadActiveChatMessages(Long groupId, Long foldedThroughId) {
        List<GroupMessage> recent = messageRepository.findByGroupIdOrderByIdDesc(
                groupId, PageRequest.of(0, SCAN_LIMIT));
        List<GroupMessage> chronological = new ArrayList<>(recent);
        java.util.Collections.reverse(chronological);
        List<GroupMessage> out = new ArrayList<>();
        for (GroupMessage m : chronological) {
            // 系统事件保留；审批卡仍跳过
            if (m.getMessageKind() == GroupMessage.MessageKind.MENTION_APPROVAL) continue;
            if (foldedThroughId != null && m.getId() != null && m.getId() <= foldedThroughId) continue;
            out.add(m);
        }
        return out;
    }

    private String buildTranscript(SessionState prior, List<GroupMessage> old, UserAgent ua) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Prior session state (JSON)\n");
        sb.append(prior.toJson(objectMapper)).append("\n\n");
        sb.append("### Group chat turns to fold (agent=").append(ua.getName()).append(")\n");
        for (GroupMessage m : old) {
            sb.append(formatLine(m)).append('\n');
            if (sb.length() >= TRANSCRIPT_MAX_CHARS) {
                sb.append("\n…(transcript truncated)\n");
                break;
            }
        }
        return sb.toString();
    }

    private String formatLine(GroupMessage m) {
        String who = switch (m.getSenderType()) {
            case USER -> {
                String label = "用户#" + m.getSenderUserId();
                if (m.getSenderUserId() != null) {
                    User u = userRepository.findById(m.getSenderUserId()).orElse(null);
                    if (u != null) {
                        if (StringUtils.hasText(u.getDisplayName())) yield u.getDisplayName().trim();
                        if (StringUtils.hasText(u.getUsername())) yield u.getUsername().trim();
                    }
                }
                yield label;
            }
            case AGENT -> {
                if (m.getSenderAgentId() != null) {
                    yield userAgentRepository.findById(m.getSenderAgentId())
                            .map(UserAgent::getName)
                            .filter(StringUtils::hasText)
                            .orElse("Agent#" + m.getSenderAgentId());
                }
                yield "Agent";
            }
            case SYSTEM -> "系统";
        };
        return who + ": " + (m.getContent() != null ? m.getContent() : "");
    }

    private GroupAgentContext getOrCreate(Long groupId, Long userAgentId) {
        return contextRepository.findByGroupIdAndUserAgentId(groupId, userAgentId)
                .orElseGet(() -> {
                    GroupAgentContext c = new GroupAgentContext();
                    c.setGroupId(groupId);
                    c.setUserAgentId(userAgentId);
                    return contextRepository.save(c);
                });
    }

    private GroupMember requireAgentMember(Long groupId, Long userAgentId) {
        return memberRepository.findByGroupIdAndUserAgentId(groupId, userAgentId)
                .filter(m -> m.getMemberType() == GroupMember.MemberType.AGENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "群内无此 Agent"));
    }

    private String safePersona(Long userAgentId) {
        try {
            String p = agentPromptService.activePersonaPrompt(userAgentId);
            return p != null ? p : "";
        } catch (Exception e) {
            return userAgentRepository.findById(userAgentId)
                    .map(ua -> ua.getPersonality() != null ? ua.getPersonality() : "")
                    .orElse("");
        }
    }
}
