package com.openforge.aimate.group;

import com.openforge.aimate.domain.GroupMember;
import com.openforge.aimate.domain.GroupMessage;
import com.openforge.aimate.domain.GroupMessageRead;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.repository.GroupMemberRepository;
import com.openforge.aimate.repository.GroupMessageReadRepository;
import com.openforge.aimate.repository.GroupMessageRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import com.openforge.aimate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 给 Agent / 本机桥的「人类可见」结构化会话视图：
 * 发送者、时间、@、引用、已读/未读、系统事件，替代「谁: 正文」纯文本拼接。
 */
@Service
@RequiredArgsConstructor
public class GroupConversationViewService {

    private static final Pattern MENTION = Pattern.compile("@([^\\s@，,。.！!？?]+)");
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GroupMessageRepository messageRepository;
    private final GroupMessageReadRepository readRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final UserAgentRepository userAgentRepository;

    /**
     * 拉取近期消息并组装结构化行（含系统事件；不含审批卡）。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> buildStructuredMessages(Long groupId, int limit) {
        int cap = Math.max(8, Math.min(80, limit));
        List<GroupMessage> recent = messageRepository.findByGroupIdOrderByIdDesc(
                groupId, PageRequest.of(0, cap));
        List<GroupMessage> chronological = new ArrayList<>(recent);
        java.util.Collections.reverse(chronological);

        List<GroupMessage> filtered = new ArrayList<>();
        for (GroupMessage m : chronological) {
            if (m.getMessageKind() == GroupMessage.MessageKind.MENTION_APPROVAL) continue;
            filtered.add(m);
        }
        if (filtered.isEmpty()) return List.of();

        Roster roster = loadRoster(groupId);
        Map<Long, GroupMessage> byId = filtered.stream()
                .filter(m -> m.getId() != null)
                .collect(Collectors.toMap(GroupMessage::getId, m -> m, (a, b) -> a, LinkedHashMap::new));

        // 引用可能在窗口外
        Set<Long> replyIds = filtered.stream()
                .map(GroupMessage::getReplyToId)
                .filter(Objects::nonNull)
                .filter(id -> !byId.containsKey(id))
                .collect(Collectors.toSet());
        if (!replyIds.isEmpty()) {
            for (GroupMessage p : messageRepository.findAllById(replyIds)) {
                byId.put(p.getId(), p);
            }
        }

        List<Long> msgIds = filtered.stream().map(GroupMessage::getId).filter(Objects::nonNull).toList();
        Map<Long, List<GroupMessageRead>> readsByMsg = msgIds.isEmpty()
                ? Map.of()
                : readRepository.findByMessageIdIn(msgIds).stream()
                .collect(Collectors.groupingBy(GroupMessageRead::getMessageId));

        List<Map<String, Object>> out = new ArrayList<>();
        for (GroupMessage m : filtered) {
            out.add(toStructured(m, byId, readsByMsg.getOrDefault(m.getId(), List.of()), roster));
        }
        return out;
    }

    /** 渲染给大模型的可读 transcript（云端群聊 / 投票）。 */
    public String renderTranscript(List<Map<String, Object>> structured, int maxChars) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Group conversation (human-visible view)\n");
        sb.append("每条含：时间、消息号、发送者、引用、@、正文、已读/未读。系统事件也在流里。\n\n");
        for (Map<String, Object> row : structured) {
            sb.append(renderOne(row)).append('\n');
        }
        String text = sb.toString().trim();
        int lim = Math.max(1200, maxChars);
        if (text.length() > lim) {
            return text.substring(text.length() - lim);
        }
        return text;
    }

    private Map<String, Object> toStructured(
            GroupMessage m,
            Map<Long, GroupMessage> byId,
            List<GroupMessageRead> reads,
            Roster roster
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.getId());
        row.put("msgId", m.getId());
        row.put("senderType", m.getSenderType() != null ? m.getSenderType().name() : null);
        row.put("messageKind", m.getMessageKind() != null ? m.getMessageKind().name() : "CHAT");
        row.put("createdAt", m.getCreateTime() != null ? m.getCreateTime().format(TIME) : null);
        row.put("content", m.getContent());

        String senderName;
        String senderRole;
        if (m.getSenderType() == GroupMessage.SenderType.USER) {
            senderName = roster.userName(m.getSenderUserId());
            senderRole = "人类";
            row.put("senderUserId", m.getSenderUserId());
        } else if (m.getSenderType() == GroupMessage.SenderType.AGENT) {
            senderName = roster.agentName(m.getSenderAgentId());
            senderRole = "Agent";
            row.put("senderAgentId", m.getSenderAgentId());
        } else {
            senderName = "系统";
            senderRole = "系统事件";
        }
        row.put("senderName", senderName);
        row.put("senderRole", senderRole);

        List<String> mentions = parseMentions(m.getContent(), roster);
        row.put("mentions", mentions);

        if (m.getReplyToId() != null) {
            Map<String, Object> quote = new LinkedHashMap<>();
            quote.put("id", m.getReplyToId());
            GroupMessage parent = byId.get(m.getReplyToId());
            if (parent == null) {
                quote.put("deleted", true);
                quote.put("preview", "原消息已删除");
            } else {
                quote.put("deleted", false);
                quote.put("senderType", parent.getSenderType() != null ? parent.getSenderType().name() : null);
                quote.put("senderName", resolveSenderName(parent, roster));
                String preview = parent.getContent() != null ? parent.getContent().trim() : "";
                if (preview.length() > 160) preview = preview.substring(0, 160) + "…";
                quote.put("preview", preview);
                if (parent.getCreateTime() != null) {
                    quote.put("createdAt", parent.getCreateTime().format(TIME));
                }
            }
            row.put("replyTo", quote);
            row.put("replyToId", m.getReplyToId());
        }

        List<String> readNames = new ArrayList<>();
        Set<Long> readMemberIds = new HashSet<>();
        for (GroupMessageRead r : reads) {
            if (r.getMemberId() == null) continue;
            readMemberIds.add(r.getMemberId());
            String n = roster.memberDisplay(r.getMemberId());
            if (StringUtils.hasText(n)) readNames.add(n);
        }
        row.put("readBy", readNames);

        List<String> unreadNames = new ArrayList<>();
        for (RosterMember rm : roster.members) {
            if (!readMemberIds.contains(rm.memberId)) {
                unreadNames.add(rm.displayName);
            }
        }
        row.put("unreadBy", unreadNames);
        return row;
    }

    private String renderOne(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder();
        String time = str(row.get("createdAt"));
        Object id = row.get("id");
        String role = str(row.get("senderRole"));
        String name = str(row.get("senderName"));
        String type = str(row.get("senderType"));

        sb.append('[').append(time.isEmpty() ? "?" : time).append("] ");
        if (id != null) sb.append('#').append(id).append(' ');
        if ("SYSTEM".equals(type) || "系统事件".equals(role)) {
            sb.append("【系统】 ");
        } else {
            sb.append(role).append('·').append(name).append("：");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> quote = row.get("replyTo") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : null;
        if (quote != null) {
            sb.append('\n').append("  ↪ 引用 #").append(quote.get("id")).append(' ');
            if (Boolean.TRUE.equals(quote.get("deleted"))) {
                sb.append("（原消息已删除）");
            } else {
                sb.append(str(quote.get("senderName"))).append("：「")
                        .append(str(quote.get("preview"))).append('」');
            }
            sb.append('\n').append("  ");
        }

        @SuppressWarnings("unchecked")
        List<String> mentions = row.get("mentions") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        if (!mentions.isEmpty()) {
            sb.append("@[").append(String.join(", ", mentions)).append("] ");
        }

        String content = str(row.get("content"));
        sb.append(content.isEmpty() && "SYSTEM".equals(type) ? "（系统事件）" : content);

        @SuppressWarnings("unchecked")
        List<String> readBy = row.get("readBy") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        @SuppressWarnings("unchecked")
        List<String> unreadBy = row.get("unreadBy") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        if (!readBy.isEmpty() || !unreadBy.isEmpty()) {
            sb.append('\n').append("  已读：")
                    .append(readBy.isEmpty() ? "（暂无）" : String.join("、", readBy));
            if (!unreadBy.isEmpty() && unreadBy.size() <= 12) {
                sb.append("｜未读：").append(String.join("、", unreadBy));
            } else if (unreadBy.size() > 12) {
                sb.append("｜未读：").append(unreadBy.size()).append(" 人");
            }
        }
        return sb.toString();
    }

    private List<String> parseMentions(String content, Roster roster) {
        if (!StringUtils.hasText(content) || !content.contains("@")) return List.of();
        List<String> hits = new ArrayList<>();
        Matcher m = MENTION.matcher(content);
        while (m.find()) {
            String token = m.group(1).trim();
            if (token.isEmpty()) continue;
            String resolved = roster.resolveMention(token);
            hits.add(resolved != null ? resolved : token);
        }
        return hits;
    }

    private String resolveSenderName(GroupMessage m, Roster roster) {
        if (m.getSenderType() == GroupMessage.SenderType.USER) return roster.userName(m.getSenderUserId());
        if (m.getSenderType() == GroupMessage.SenderType.AGENT) return roster.agentName(m.getSenderAgentId());
        return "系统";
    }

    private Roster loadRoster(Long groupId) {
        List<GroupMember> members = memberRepository.findByGroupIdOrderByIdAsc(groupId);
        List<RosterMember> list = new ArrayList<>();
        Map<Long, String> users = new HashMap<>();
        Map<Long, String> agents = new HashMap<>();
        Map<Long, String> memberNames = new HashMap<>();
        List<String> allNames = new ArrayList<>();

        for (GroupMember m : members) {
            String display;
            if (m.getMemberType() == GroupMember.MemberType.USER && m.getUserId() != null) {
                display = StringUtils.hasText(m.getNickname())
                        ? m.getNickname().trim()
                        : userDisplay(m.getUserId());
                users.put(m.getUserId(), display);
            } else if (m.getMemberType() == GroupMember.MemberType.AGENT && m.getUserAgentId() != null) {
                display = agentDisplay(m.getUserAgentId());
                agents.put(m.getUserAgentId(), display);
            } else {
                continue;
            }
            list.add(new RosterMember(m.getId(), display, m.getMemberType().name()));
            memberNames.put(m.getId(), display);
            allNames.add(display);
        }
        return new Roster(list, users, agents, memberNames, allNames);
    }

    private String userDisplay(Long userId) {
        if (userId == null) return "用户";
        User u = userRepository.findById(userId).orElse(null);
        if (u == null) return "用户#" + userId;
        if (StringUtils.hasText(u.getDisplayName())) return u.getDisplayName().trim();
        if (StringUtils.hasText(u.getUsername())) return u.getUsername().trim();
        return "用户#" + userId;
    }

    private String agentDisplay(Long agentId) {
        if (agentId == null) return "Agent";
        return userAgentRepository.findById(agentId)
                .map(UserAgent::getName)
                .filter(StringUtils::hasText)
                .orElse("Agent#" + agentId);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private record RosterMember(Long memberId, String displayName, String memberType) {}

    private record Roster(
            List<RosterMember> members,
            Map<Long, String> users,
            Map<Long, String> agents,
            Map<Long, String> memberNames,
            List<String> allNames
    ) {
        String userName(Long id) {
            return users.getOrDefault(id, id == null ? "用户" : "用户#" + id);
        }

        String agentName(Long id) {
            return agents.getOrDefault(id, id == null ? "Agent" : "Agent#" + id);
        }

        String memberDisplay(Long memberId) {
            return memberNames.getOrDefault(memberId, "成员#" + memberId);
        }

        String resolveMention(String token) {
            String t = token.toLowerCase(Locale.ROOT);
            for (String n : allNames) {
                if (n.equalsIgnoreCase(token)) return n;
                if (n.toLowerCase(Locale.ROOT).startsWith(t) && t.length() >= 2) return n;
            }
            return null;
        }
    }
}
