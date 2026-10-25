package com.openforge.aimate.group;

import com.openforge.aimate.agent.UserAgentIdentityService;
import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.domain.ChatGroup;
import com.openforge.aimate.domain.GroupMember;
import com.openforge.aimate.domain.GroupMessage;
import com.openforge.aimate.domain.SessionMessage;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.repository.AgentSessionRepository;
import com.openforge.aimate.repository.ChatGroupRepository;
import com.openforge.aimate.repository.GroupMemberRepository;
import com.openforge.aimate.repository.GroupMessageRepository;
import com.openforge.aimate.repository.SessionMessageRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import com.openforge.aimate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 微信式统一会话列表：Agent 单聊（一人一聊）+ 群聊；大标题为对方名，小字为最近消息。
 */
@Service
@RequiredArgsConstructor
public class ConversationInboxService {

    private static final int PREVIEW_MAX = 48;

    private final AgentSessionRepository sessionRepository;
    private final ChatGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserAgentRepository userAgentRepository;
    private final UserRepository userRepository;
    private final SessionMessageRepository sessionMessageRepository;
    private final GroupMessageRepository groupMessageRepository;
    private final UserAgentIdentityService identityService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listInbox(Long userId, int limit) {
        return listInbox(userId, limit, false);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listInbox(Long userId, int limit, boolean archived) {
        int lim = Math.min(Math.max(1, limit), 80);
        List<Map<String, Object>> items = new ArrayList<>();

        List<AgentSession> sessions = archived
                ? sessionRepository.findByUserIdAndHiddenTrueOrderByUpdateTimeDesc(userId, PageRequest.of(0, lim * 3))
                : sessionRepository.findByUserIdAndHiddenFalseOrderByLastUserMessageAtDescUpdateTimeDesc(
                        userId, PageRequest.of(0, Math.max(lim * 3, 60)));

        // 一人一聊：同一 userAgentId 只保留最近一条
        Set<Long> seenAgents = new HashSet<>();
        for (AgentSession s : sessions) {
            Long agentId = s.getUserAgentId();
            if (agentId != null) {
                if (!seenAgents.add(agentId)) continue;
            }
            Map<String, Object> item = sessionItem(s);
            item.put("archived", archived);
            items.add(item);
            if (items.size() >= lim) break;
        }

        for (ChatGroup g : groupRepository.findActiveForUser(userId)) {
            GroupMember me = memberRepository.findByGroupIdAndUserId(g.getId(), userId).orElse(null);
            if (me == null || me.isArchived() != archived) continue;
            Map<String, Object> item = groupItem(g, me);
            item.put("archived", archived);
            items.add(item);
        }

        items.sort(Comparator
                .comparing((Map<String, Object> m) -> m.get("pinnedAt") != null ? 0 : 1)
                .thenComparing(m -> {
                    Object p = m.get("pinnedAt");
                    return p instanceof LocalDateTime t ? t : LocalDateTime.MIN;
                }, Comparator.reverseOrder())
                .thenComparing(ConversationInboxService::activityTime, Comparator.reverseOrder()));

        if (items.size() > lim) {
            return items.subList(0, lim);
        }
        return items;
    }

    private Map<String, Object> sessionItem(AgentSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "SESSION");
        m.put("id", s.getSessionId());
        m.put("sessionId", s.getSessionId());
        m.put("status", s.getStatus().name());
        m.put("userAgentId", s.getUserAgentId());
        m.put("pinned", s.getPinnedAt() != null);
        m.put("pinnedAt", s.getPinnedAt());
        LocalDateTime activity = s.getLastUserMessageAt() != null
                ? s.getLastUserMessageAt()
                : (s.getUpdateTime() != null ? s.getUpdateTime() : s.getCreateTime());
        m.put("lastUserMessageAt", s.getLastUserMessageAt());
        m.put("updateTime", activity);
        m.put("createTime", s.getCreateTime());

        String agentName = null;
        String displayTitle = null;
        if (s.getUserAgentId() != null) {
            var uaOpt = userAgentRepository.findById(s.getUserAgentId());
            if (uaOpt.isPresent()) {
                UserAgent ua = uaOpt.get();
                agentName = ua.getName();
                displayTitle = identityService.displayTitle(ua);
                m.put("agentName", agentName);
                m.put("agentOriginalName", identityService.originalName(ua));
                m.put("agentCareerName", identityService.careerName(ua));
                m.put("agentDisplayTitle", displayTitle);
                m.put("avatarUrl", ua.getAvatarUrl());
                m.put("avatarUrls", List.of(resolveAgentAvatar(ua)));
            }
        } else {
            m.put("avatarUrls", List.of());
        }
        // 微信式：大标题 = 真名 · 招聘职业（便于区分）
        m.put("title", StringUtils.hasText(displayTitle) ? displayTitle
                : (StringUtils.hasText(agentName) ? agentName
                : (StringUtils.hasText(s.getTaskDescription()) ? s.getTaskDescription() : "会话")));
        m.put("lastMessagePreview", lastSessionPreview(s));
        return m;
    }

    private String lastSessionPreview(AgentSession s) {
        // 多取几条，跳过空占位 / 仅工具附属，保证列表一定能落到有正文的消息
        List<SessionMessage> latest = sessionMessageRepository
                .findMainMessagesBySessionIdOrderBySeqDesc(s.getId(), PageRequest.of(0, 12));
        for (SessionMessage msg : latest) {
            String role = msg.getRole() != null ? msg.getRole() : "";
            String content = firstNonBlank(msg.getContent(), msg.getThinkingContent());
            if (!StringUtils.hasText(content)) continue;
            String prefix = "user".equalsIgnoreCase(role) ? "我: " : "";
            return prefix + truncate(plainPreview(content));
        }
        return "暂无消息";
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) return a.trim();
        if (StringUtils.hasText(b)) return b.trim();
        return null;
    }

    /** 列表预览：去掉粗略 markdown，压成一行 */
    private static String plainPreview(String raw) {
        String s = raw.replace('\n', ' ').replace('\r', ' ');
        s = s.replace("**", "").replace("`", "").replaceAll("#+\\s*", "");
        while (s.contains("  ")) s = s.replace("  ", " ");
        return s.trim();
    }

    private static LocalDateTime activityTime(Map<String, Object> m) {
        Object lastUser = m.get("lastUserMessageAt");
        if (lastUser instanceof LocalDateTime t) return t;
        Object u = m.get("updateTime");
        if (u instanceof LocalDateTime t) return t;
        Object c = m.get("createTime");
        if (c instanceof LocalDateTime t) return t;
        return LocalDateTime.MIN;
    }

    private Map<String, Object> groupItem(ChatGroup g, GroupMember me) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "GROUP");
        m.put("id", g.getPublicId());
        m.put("publicId", g.getPublicId());
        String title = StringUtils.hasText(me.getRemark()) ? me.getRemark() : g.getTitle();
        m.put("title", title);
        m.put("groupTitle", g.getTitle());
        m.put("remark", me.getRemark());
        m.put("pinned", me.getPinnedAt() != null);
        m.put("pinnedAt", me.getPinnedAt());
        m.put("updateTime", g.getUpdateTime() != null ? g.getUpdateTime() : g.getCreateTime());
        m.put("createTime", g.getCreateTime());
        m.put("myRole", me.getRole().name());
        List<String> avatars = memberAvatars(g.getId(), 9);
        m.put("avatarUrls", avatars);
        if (StringUtils.hasText(g.getAvatarUrl())) {
            m.put("avatarUrl", g.getAvatarUrl());
        }
        m.put("lastMessagePreview", lastGroupPreview(g.getId()));
        return m;
    }

    private String lastGroupPreview(Long groupId) {
        List<GroupMessage> latest = groupMessageRepository.findByGroupIdOrderByIdDesc(groupId, PageRequest.of(0, 8));
        for (GroupMessage msg : latest) {
            String content = msg.getContent() != null ? msg.getContent().trim() : "";
            if (!StringUtils.hasText(content)) continue;
            return truncate(plainPreview(content));
        }
        return "暂无消息";
    }

    private static String truncate(String s) {
        if (s.length() <= PREVIEW_MAX) return s;
        return s.substring(0, PREVIEW_MAX) + "…";
    }

    private List<String> memberAvatars(Long groupId, int max) {
        List<String> out = new ArrayList<>();
        for (GroupMember mem : memberRepository.findByGroupIdOrderByIdAsc(groupId)) {
            if (out.size() >= max) break;
            String url = null;
            if (mem.getMemberType() == GroupMember.MemberType.USER && mem.getUserId() != null) {
                url = userRepository.findById(mem.getUserId())
                        .map(User::getAvatarUrl)
                        .orElse(null);
                if (!StringUtils.hasText(url)) {
                    url = "letter:" + userRepository.findById(mem.getUserId())
                            .map(u -> StringUtils.hasText(u.getDisplayName()) ? u.getDisplayName() : u.getUsername())
                            .orElse("?");
                }
            } else if (mem.getMemberType() == GroupMember.MemberType.AGENT && mem.getUserAgentId() != null) {
                url = userAgentRepository.findById(mem.getUserAgentId())
                        .map(this::resolveAgentAvatar)
                        .orElse(null);
            }
            if (StringUtils.hasText(url)) out.add(url);
        }
        return out;
    }

    private String resolveAgentAvatar(UserAgent ua) {
        if (StringUtils.hasText(ua.getAvatarUrl())) return ua.getAvatarUrl();
        return "agent:" + (ua.getExternalAgentKey() != null ? ua.getExternalAgentKey() : ua.getId());
    }
}
