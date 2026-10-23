package com.openforge.aimate.group;

import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.domain.ChatGroup;
import com.openforge.aimate.domain.GroupMember;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.repository.AgentSessionRepository;
import com.openforge.aimate.repository.ChatGroupRepository;
import com.openforge.aimate.repository.GroupMemberRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 微信式统一会话列表：Agent 单聊 + 群聊，支持置顶排序，排除归档。
 */
@Service
@RequiredArgsConstructor
public class ConversationInboxService {

    private final AgentSessionRepository sessionRepository;
    private final ChatGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserAgentRepository userAgentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listInbox(Long userId, int limit) {
        int lim = Math.min(Math.max(1, limit), 80);
        List<Map<String, Object>> items = new ArrayList<>();

        List<AgentSession> sessions = sessionRepository
                .findByUserIdAndHiddenFalseOrderByCreateTimeDesc(userId, PageRequest.of(0, lim));
        for (AgentSession s : sessions) {
            items.add(sessionItem(s));
        }

        for (ChatGroup g : groupRepository.findActiveForUser(userId)) {
            GroupMember me = memberRepository.findByGroupIdAndUserId(g.getId(), userId).orElse(null);
            if (me == null || me.isArchived()) continue;
            items.add(groupItem(g, me));
        }

        items.sort(Comparator
                .comparing((Map<String, Object> m) -> m.get("pinnedAt") != null ? 0 : 1)
                .thenComparing(m -> {
                    Object p = m.get("pinnedAt");
                    return p instanceof LocalDateTime t ? t : LocalDateTime.MIN;
                }, Comparator.reverseOrder())
                .thenComparing(m -> {
                    Object u = m.get("updateTime");
                    return u instanceof LocalDateTime t ? t : LocalDateTime.MIN;
                }, Comparator.reverseOrder()));

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
        m.put("title", StringUtils.hasText(s.getTaskDescription()) ? s.getTaskDescription() : "会话");
        m.put("status", s.getStatus().name());
        m.put("userAgentId", s.getUserAgentId());
        m.put("pinned", s.getPinnedAt() != null);
        m.put("pinnedAt", s.getPinnedAt());
        m.put("updateTime", s.getUpdateTime() != null ? s.getUpdateTime() : s.getCreateTime());
        m.put("createTime", s.getCreateTime());
        if (s.getUserAgentId() != null) {
            userAgentRepository.findById(s.getUserAgentId()).ifPresent(ua -> {
                m.put("agentName", ua.getName());
                m.put("avatarUrl", ua.getAvatarUrl());
                m.put("avatarUrls", List.of(resolveAgentAvatar(ua)));
            });
        } else {
            m.put("avatarUrls", List.of());
        }
        return m;
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
        return m;
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
