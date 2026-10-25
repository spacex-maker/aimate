package com.openforge.aimate.memory;

import com.openforge.aimate.agent.ContextCompressService;
import com.openforge.aimate.agent.UserAgentIdentityService;
import com.openforge.aimate.agent.UserToolSettingsService;
import com.openforge.aimate.agent.dto.SessionState;
import com.openforge.aimate.domain.ChatGroup;
import com.openforge.aimate.domain.GroupMember;
import com.openforge.aimate.domain.GroupMessage;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.domain.UserProfile;
import com.openforge.aimate.repository.ChatGroupRepository;
import com.openforge.aimate.repository.GroupMemberRepository;
import com.openforge.aimate.repository.GroupMessageRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import com.openforge.aimate.repository.UserProfileRepository;
import com.openforge.aimate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群聊记忆 moderator：任务结束后由服务端提炼耐久事实写入 Agent Soul，
 * 不依赖本机 Cursor/提示词自觉 POST（对齐 external memory moderator / Generative Agents 写入侧）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMemoryCaptureService {

    private static final int RECENT_FOR_TRANSCRIPT = 12;
    private static final int EPISODE_MAX = 480;
    private static final long DEDUP_TTL_MS = 120_000L;

    private final ContextCompressService contextCompressService;
    private final SoulMemoryBridge soulMemoryBridge;
    private final UserToolSettingsService userToolSettingsService;
    private final UserAgentRepository userAgentRepository;
    private final ChatGroupRepository groupRepository;
    private final GroupMessageRepository messageRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserAgentIdentityService identityService;

    /** agentId|hash → expireAt */
    private final ConcurrentHashMap<String, Long> recentClaims = new ConcurrentHashMap<>();

    /**
     * Agent 被拉进群后：像真人入群一样，把群基本信息与花名册记进自己的 Soul。
     */
    @Async
    public void rememberGroupJoined(Long userAgentId, Long groupId) {
        if (userAgentId == null || groupId == null || !soulMemoryBridge.isEnabled()) {
            return;
        }
        try {
            UserAgent me = userAgentRepository.findById(userAgentId).orElse(null);
            if (me == null || !StringUtils.hasText(me.getSoulAccessToken())) {
                return;
            }
            if (!userToolSettingsService.isMemoryEnabled(me.getUserId(), me.getId())) {
                return;
            }
            ChatGroup group = groupRepository.findById(groupId).orElse(null);
            if (group == null) return;

            String project = "group:" + group.getPublicId();
            String gName = StringUtils.hasText(group.getTitle()) ? group.getTitle().trim() : group.getPublicId();
            String ownerName = resolveUserDisplay(group.getOwnerUserId());

            StringBuilder groupFact = new StringBuilder();
            groupFact.append("我加入了群「").append(gName).append("」（群ID ").append(group.getPublicId()).append("）。");
            if (StringUtils.hasText(ownerName)) {
                groupFact.append(" 群主是").append(ownerName).append("。");
            }
            if (StringUtils.hasText(group.getAnnouncement())) {
                String ann = group.getAnnouncement().trim();
                if (ann.length() > 280) ann = ann.substring(0, 280) + "…";
                groupFact.append(" 群公告：").append(ann);
            }
            soulMemoryBridge.store(
                    groupFact.toString(), "FACT", 0.88f, me.getUserId(), me.getId(), project);

            List<GroupMember> members = memberRepository.findByGroupIdOrderByIdAsc(groupId);
            List<String> humanLines = new ArrayList<>();
            List<String> agentLines = new ArrayList<>();
            for (GroupMember m : members) {
                if (m.getMemberType() == GroupMember.MemberType.USER && m.getUserId() != null) {
                    humanLines.add(describeHumanMember(m));
                } else if (m.getMemberType() == GroupMember.MemberType.AGENT && m.getUserAgentId() != null) {
                    if (m.getUserAgentId().equals(me.getId())) {
                        agentLines.add("我自己（" + me.getName() + "）也在这个群里");
                    } else {
                        agentLines.add(describeAgentMember(m));
                    }
                }
            }

            StringBuilder roster = new StringBuilder();
            roster.append("群「").append(gName).append("」里我认识的人：");
            if (!humanLines.isEmpty()) {
                roster.append(" 人类成员——").append(String.join("；", humanLines)).append("。");
            }
            if (!agentLines.isEmpty()) {
                roster.append(" 同事 Agent——").append(String.join("；", agentLines)).append("。");
            }
            if (humanLines.isEmpty() && agentLines.isEmpty()) {
                roster.append("目前花名册还很短，我刚进群。");
            }
            String rosterText = roster.toString();
            if (rosterText.length() > 1200) {
                rosterText = rosterText.substring(0, 1200) + "…";
            }
            soulMemoryBridge.store(
                    rosterText, "FACT", 0.82f, me.getUserId(), me.getId(), project);

            // 每人单独一条，方便单聊按人名召回（人数过多时只写前 20，花名册总览仍保留）
            int personWrites = 0;
            for (GroupMember m : members) {
                if (personWrites >= 20) break;
                if (m.getMemberType() == GroupMember.MemberType.USER && m.getUserId() != null) {
                    String one = "在群「" + gName + "」里，" + describeHumanMember(m) + "。";
                    soulMemoryBridge.store(one, "FACT", 0.72f, me.getUserId(), me.getId(), project);
                    personWrites++;
                } else if (m.getMemberType() == GroupMember.MemberType.AGENT
                        && m.getUserAgentId() != null
                        && !m.getUserAgentId().equals(me.getId())) {
                    String one = "在群「" + gName + "」里，" + describeAgentMember(m) + "。";
                    soulMemoryBridge.store(one, "FACT", 0.7f, me.getUserId(), me.getId(), project);
                    personWrites++;
                }
            }

            String episode = "我被邀请进了群「" + gName + "」，已经记住群里有谁、各自大概是什么人。";
            soulMemoryBridge.store(
                    episode, "EPISODE", 0.65f, me.getUserId(), me.getId(), project);

            log.info("[GroupMemory] join-roster agent={} group={} humans={} agents={}",
                    me.getId(), group.getPublicId(), humanLines.size(), agentLines.size());
        } catch (Exception e) {
            log.warn("[GroupMemory] rememberGroupJoined failed agent={}: {}", userAgentId, e.toString());
        }
    }

    /**
     * 群结构/重要事件：广播给群内所有已绑定 Soul 的 Agent（可排除刚写过完整花名册的入群者）。
     */
    @Async
    public void rememberEventForAllAgents(Long groupId, String claim, String kind, float importance, Long excludeAgentId) {
        if (groupId == null || !StringUtils.hasText(claim) || !soulMemoryBridge.isEnabled()) {
            return;
        }
        ChatGroup group = groupRepository.findById(groupId).orElse(null);
        if (group == null) return;
        String project = "group:" + group.getPublicId();
        String gName = StringUtils.hasText(group.getTitle()) ? group.getTitle().trim() : group.getPublicId();
        String text = claim.trim();
        if (!text.contains(gName) && !text.contains("群「")) {
            text = "群「" + gName + "」：" + text;
        }
        if (text.length() > 480) text = text.substring(0, 480) + "…";
        String kindUse = StringUtils.hasText(kind) ? kind : "FACT";
        float imp = Math.max(0.45f, Math.min(1f, importance));

        for (GroupMember m : memberRepository.findByGroupIdOrderByIdAsc(groupId)) {
            if (m.getMemberType() != GroupMember.MemberType.AGENT || m.getUserAgentId() == null) continue;
            if (excludeAgentId != null && excludeAgentId.equals(m.getUserAgentId())) continue;
            try {
                UserAgent ua = userAgentRepository.findById(m.getUserAgentId()).orElse(null);
                if (ua == null || !StringUtils.hasText(ua.getSoulAccessToken())) continue;
                if (!userToolSettingsService.isMemoryEnabled(ua.getUserId(), ua.getId())) continue;
                if (isDuplicate(ua.getId(), text)) continue;
                soulMemoryBridge.store(text, kindUse, imp, ua.getUserId(), ua.getId(), project);
                markSeen(ua.getId(), text);
            } catch (Exception e) {
                log.debug("[GroupMemory] broadcast skip agent={}: {}", m.getUserAgentId(), e.getMessage());
            }
        }
    }

    /**
     * 用户在群里说了可能重要的话：在场 Agent 各记一条情节（启发式，不整段 dump）。
     */
    @Async
    public void observeImportantUserMessage(Long groupId, Long messageId) {
        if (groupId == null || messageId == null || !soulMemoryBridge.isEnabled()) return;
        GroupMessage msg = messageRepository.findById(messageId).orElse(null);
        if (msg == null || msg.getSenderType() != GroupMessage.SenderType.USER) return;
        String content = msg.getContent() != null ? msg.getContent().trim() : "";
        if (!looksImportant(content)) return;

        ChatGroup group = groupRepository.findById(groupId).orElse(null);
        if (group == null) return;
        String gName = StringUtils.hasText(group.getTitle()) ? group.getTitle().trim() : group.getPublicId();
        String who = resolveUserDisplay(msg.getSenderUserId());
        if (!StringUtils.hasText(who)) who = "有人";
        String brief = content.length() > 220 ? content.substring(0, 220) + "…" : content;
        String episode = "在群「" + gName + "」里，" + who + "说：" + brief;
        rememberEventForAllAgents(groupId, episode, "EPISODE", 0.62f, null);
    }

    /** 工具：当前群基本信息 */
    public Map<String, Object> groupInfoMap(Long groupId) {
        Map<String, Object> out = new LinkedHashMap<>();
        ChatGroup g = groupRepository.findById(groupId).orElse(null);
        if (g == null) {
            out.put("error", "群不存在");
            return out;
        }
        out.put("publicId", g.getPublicId());
        out.put("title", g.getTitle());
        out.put("announcement", g.getAnnouncement());
        out.put("owner", resolveUserDisplay(g.getOwnerUserId()));
        out.put("ownerUserId", g.getOwnerUserId());
        out.put("status", g.getStatus() != null ? g.getStatus().name() : null);
        out.put("membersInviteEnabled", g.isMembersInviteEnabled());
        List<GroupMember> members = memberRepository.findByGroupIdOrderByIdAsc(groupId);
        long humans = members.stream().filter(m -> m.getMemberType() == GroupMember.MemberType.USER).count();
        long agents = members.stream().filter(m -> m.getMemberType() == GroupMember.MemberType.AGENT).count();
        out.put("humanCount", humans);
        out.put("agentCount", agents);
        out.put("memberCount", members.size());
        return out;
    }

    /** 工具：成员列表（可关键字过滤） */
    public List<Map<String, Object>> memberInfoList(Long groupId, String keyword) {
        String kw = keyword != null ? keyword.trim().toLowerCase(Locale.ROOT) : "";
        List<Map<String, Object>> out = new ArrayList<>();
        for (GroupMember m : memberRepository.findByGroupIdOrderByIdAsc(groupId)) {
            Map<String, Object> row = memberInfoMap(m);
            if (row.isEmpty()) continue;
            if (StringUtils.hasText(kw)) {
                String blob = String.valueOf(row).toLowerCase(Locale.ROOT);
                if (!blob.contains(kw)) continue;
            }
            out.add(row);
        }
        return out;
    }

    /** 工具：按名字找一位成员 */
    public Map<String, Object> findMemberInfo(Long groupId, String name) {
        if (!StringUtils.hasText(name)) return Map.of();
        String q = name.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> best = Map.of();
        for (GroupMember m : memberRepository.findByGroupIdOrderByIdAsc(groupId)) {
            Map<String, Object> row = memberInfoMap(m);
            if (row.isEmpty()) continue;
            String display = String.valueOf(row.getOrDefault("displayName", "")).toLowerCase(Locale.ROOT);
            String detail = String.valueOf(row.getOrDefault("detail", "")).toLowerCase(Locale.ROOT);
            if (display.equals(q) || display.contains(q) || detail.contains(q)) {
                if (display.equals(q)) return row;
                best = row;
            }
        }
        return best;
    }

    private Map<String, Object> memberInfoMap(GroupMember m) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (m.getMemberType() == GroupMember.MemberType.USER && m.getUserId() != null) {
            String detail = describeHumanMember(m);
            row.put("type", "USER");
            row.put("userId", m.getUserId());
            row.put("role", m.getRole() != null ? m.getRole().name() : "MEMBER");
            row.put("displayName", firstNameFromDescribe(detail));
            row.put("nickname", m.getNickname());
            row.put("detail", detail);
            return row;
        }
        if (m.getMemberType() == GroupMember.MemberType.AGENT && m.getUserAgentId() != null) {
            String detail = describeAgentMember(m);
            row.put("type", "AGENT");
            row.put("userAgentId", m.getUserAgentId());
            row.put("role", m.getRole() != null ? m.getRole().name() : "MEMBER");
            row.put("displayName", firstNameFromDescribe(detail));
            row.put("replyMode", m.getAgentReplyMode() != null ? m.getAgentReplyMode().name() : null);
            row.put("detail", detail);
            return row;
        }
        return row;
    }

    private static String firstNameFromDescribe(String detail) {
        if (!StringUtils.hasText(detail)) return "";
        int idx = detail.indexOf('（');
        return idx > 0 ? detail.substring(0, idx).trim() : detail;
    }

    private static boolean looksImportant(String content) {
        if (!StringUtils.hasText(content)) return false;
        String t = content.trim();
        if (t.length() < 18) return false;
        if (t.length() >= 40) return true;
        String lower = t.toLowerCase(Locale.ROOT);
        String[] keys = {
                "决定", "约定", "记住", "截止", "明天", "下周", "不要", "必须", "改成", "改名",
                "加入", "退出", "公告", "分工", "负责", "deadline", "todo", "记得", "别忘"
        };
        for (String k : keys) {
            if (lower.contains(k.toLowerCase(Locale.ROOT))) return true;
        }
        return t.contains("@");
    }

    private String describeHumanMember(GroupMember m) {
        String name = resolveUserDisplay(m.getUserId());
        if (StringUtils.hasText(m.getNickname())) {
            name = m.getNickname().trim() + (StringUtils.hasText(name) ? "（账号名 " + name + "）" : "");
        }
        if (!StringUtils.hasText(name)) name = "用户#" + m.getUserId();
        String role = switch (m.getRole() != null ? m.getRole() : GroupMember.Role.MEMBER) {
            case OWNER -> "群主";
            case ADMIN -> "管理员";
            default -> "成员";
        };
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("（人类，").append(role).append("）");
        userProfileRepository.findByUserId(m.getUserId()).ifPresent(p -> {
            List<String> bits = new ArrayList<>();
            if (StringUtils.hasText(p.getOccupation())) bits.add("职业 " + p.getOccupation().trim());
            if (StringUtils.hasText(p.getGender())) bits.add("性别 " + genderZh(p.getGender()));
            if (p.getAge() != null && p.getAge() > 0) bits.add("大约 " + p.getAge() + " 岁");
            if (StringUtils.hasText(p.getBio())) {
                String bio = p.getBio().trim();
                if (bio.length() > 60) bio = bio.substring(0, 60) + "…";
                bits.add("简介 " + bio);
            }
            if (!bits.isEmpty()) sb.append("，").append(String.join("，", bits));
        });
        return sb.toString();
    }

    private String describeAgentMember(GroupMember m) {
        UserAgent ua = userAgentRepository.findById(m.getUserAgentId()).orElse(null);
        if (ua == null) return "Agent#" + m.getUserAgentId();
        String title = identityService.displayTitle(ua);
        if (!StringUtils.hasText(title)) title = ua.getName();
        String kind = ua.getKind() == UserAgent.Kind.SECRETARY ? "秘书同事" : "专业同事";
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("（Agent，").append(kind).append("）");
        if (StringUtils.hasText(ua.getGender())) {
            sb.append("，性别 ").append(genderZh(ua.getGender()));
        }
        String career = identityService.careerName(ua);
        if (StringUtils.hasText(career)) {
            sb.append("，岗位 ").append(career);
        }
        if (StringUtils.hasText(ua.getPersonality())) {
            String p = ua.getPersonality().trim();
            if (p.length() > 80) p = p.substring(0, 80) + "…";
            sb.append("，性格 ").append(p);
        }
        return sb.toString();
    }

    private String resolveUserDisplay(Long userId) {
        if (userId == null) return null;
        User u = userRepository.findById(userId).orElse(null);
        if (u == null) return null;
        if (StringUtils.hasText(u.getDisplayName())) return u.getDisplayName().trim();
        if (StringUtils.hasText(u.getUsername())) return u.getUsername().trim();
        return null;
    }

    private static String genderZh(String raw) {
        if (!StringUtils.hasText(raw)) return raw;
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "FEMALE", "女", "F" -> "女";
            case "MALE", "男", "M" -> "男";
            case "OTHER", "其他" -> "其他";
            default -> raw.trim();
        };
    }

    /**
     * 本地桥接任务成功完成后异步调用。
     */
    @Async
    public void captureAfterLocalBridgeTask(
            Long userAgentId,
            Long groupId,
            Long triggerMessageId,
            String agentResult
    ) {
        captureAfterGroupTurn(userAgentId, groupId, triggerMessageId, agentResult);
    }

    /**
     * 群里一轮有效发言后写入该 Agent 自己的 Soul（与单聊同一颗）。
     * 只提炼耐久事实 + 一句情节，不把整段群聊当一条 NOTE。
     */
    @Async
    public void captureAfterGroupTurn(
            Long userAgentId,
            Long groupId,
            Long triggerMessageId,
            String agentResult
    ) {
        if (userAgentId == null || groupId == null || !StringUtils.hasText(agentResult)) {
            return;
        }
        if (!soulMemoryBridge.isEnabled()) {
            return;
        }
        try {
            UserAgent ua = userAgentRepository.findById(userAgentId).orElse(null);
            if (ua == null || !StringUtils.hasText(ua.getSoulAccessToken())) {
                log.debug("[GroupMemory] skip: agent {} has no Soul token", userAgentId);
                return;
            }
            if (!userToolSettingsService.isMemoryEnabled(ua.getUserId(), ua.getId())) {
                return;
            }
            ChatGroup group = groupRepository.findById(groupId).orElse(null);
            GroupMessage trigger = triggerMessageId != null
                    ? messageRepository.findById(triggerMessageId).orElse(null)
                    : null;

            String transcript = buildTranscript(group, trigger, ua, agentResult);
            if (!StringUtils.hasText(transcript) || transcript.length() < 24) {
                return;
            }

            SessionState prior = SessionState.empty();
            SessionState next = contextCompressService.mergeStructuredState(prior, transcript);
            contextCompressService.writeDurableFactsToSoul(ua.getUserId(), ua.getId(), prior, next);

            String episode = buildEpisode(group, trigger, agentResult);
            if (StringUtils.hasText(episode) && !isDuplicate(ua.getId(), episode)) {
                soulMemoryBridge.store(episode, "EPISODE", 0.58f, ua.getUserId(), ua.getId());
                markSeen(ua.getId(), episode);
            }
            log.info("[GroupMemory] captured agent={} group={} decisions={} constraints={} entities={}",
                    ua.getId(),
                    groupId,
                    next.decisions() != null ? next.decisions().size() : 0,
                    next.constraints() != null ? next.constraints().size() : 0,
                    next.entities() != null ? next.entities().size() : 0);
        } catch (Exception e) {
            log.warn("[GroupMemory] capture failed agent={}: {}", userAgentId, e.toString());
        }
    }

    /**
     * 送模用的记忆块：同一颗 Soul（单聊 + 以往群聊）。记忆关闭或无内容时返回空串。
     */
    public String soulPromptBlock(Long userId, Long userAgentId, String query) {
        if (!userToolSettingsService.isMemoryEnabled(userId, userAgentId)) {
            return "";
        }
        Map<String, Object> brief = fetchSoulContextBrief(userId, userAgentId, query);
        if (brief.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("# Long-term memory (your Soul)\n");
        sb.append("这是你自己记得的事（含和用户的单聊、以往群聊）。像人一样自然用上；不要复述本段标题，不要说「根据系统记忆」。\n");
        appendMemoryLines(sb, "记得", brief.get("core"));
        appendMemoryLines(sb, "相关", brief.get("retrieved"));
        appendMemoryLines(sb, "未完成", brief.get("openCommitments"));
        String text = sb.toString().trim();
        return text.length() > 2400 ? text.substring(0, 2400) : text;
    }

    /**
     * 供派发上下文注入：拉取 Soul context 摘要（失败返回空 map）。
     */
    public Map<String, Object> fetchSoulContextBrief(Long userId, Long userAgentId, String query) {
        Map<String, Object> brief = new LinkedHashMap<>();
        if (userId == null || userAgentId == null || !soulMemoryBridge.isEnabled()) {
            return brief;
        }
        try {
            String q = query == null ? "" : query.trim();
            if (q.length() > 200) q = q.substring(0, 200);
            String path = "/context?q=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> raw = soulMemoryBridge.proxyAgentSoul(
                    userId, userAgentId, org.springframework.http.HttpMethod.GET, path, null);
            if (!Boolean.TRUE.equals(raw.get("ok"))) {
                return brief;
            }
            Object data = raw.get("data");
            if (!(data instanceof Map<?, ?> dataMap)) {
                return brief;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> dm = (Map<String, Object>) dataMap;
            Object core = dm.get("core");
            Object retrieved = dm.get("retrieved");
            Object open = dm.get("openCommitments");
            if (core != null) brief.put("core", slimList(core, 6));
            if (retrieved != null) brief.put("retrieved", slimList(retrieved, 8));
            if (open != null) brief.put("openCommitments", slimList(open, 4));
            brief.put("note", "结合上述 Soul 记忆作答；勿复述本块原文。");
        } catch (Exception e) {
            log.debug("[GroupMemory] soul context brief failed: {}", e.getMessage());
        }
        return brief;
    }

    private String buildTranscript(ChatGroup group, GroupMessage trigger, UserAgent ua, String result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Group chat memory extract for local agent.\n");
        if (group != null) {
            sb.append("Group: ").append(group.getTitle() != null ? group.getTitle() : group.getPublicId()).append('\n');
        }
        sb.append("Agent: ").append(ua.getName()).append(" (id=").append(ua.getId()).append(")\n");
        if (trigger != null && StringUtils.hasText(trigger.getContent())) {
            sb.append("User trigger:\n").append(trigger.getContent().trim()).append("\n\n");
        }
        if (group != null) {
            List<GroupMessage> recent = messageRepository.findByGroupIdOrderByIdDesc(
                    group.getId(), PageRequest.of(0, RECENT_FOR_TRANSCRIPT));
            List<GroupMessage> chronological = new ArrayList<>(recent);
            java.util.Collections.reverse(chronological);
            sb.append("Recent messages:\n");
            for (GroupMessage m : chronological) {
                String who = m.getSenderType() != null ? m.getSenderType().name() : "?";
                String content = m.getContent() != null ? m.getContent().trim() : "";
                if (content.length() > 400) content = content.substring(0, 400) + "…";
                sb.append("- ").append(who).append(": ").append(content).append('\n');
            }
            sb.append('\n');
        }
        sb.append("Agent reply:\n").append(result.trim()).append('\n');
        sb.append("\nExtract durable decisions, preferences/constraints, and entities (names/paths/ids). ");
        sb.append("Skip chit-chat and one-off task noise.\n");
        if (sb.length() > 6000) {
            return sb.substring(0, 6000);
        }
        return sb.toString();
    }

    private String buildEpisode(ChatGroup group, GroupMessage trigger, String result) {
        String gName = group != null
                ? (StringUtils.hasText(group.getTitle()) ? group.getTitle() : group.getPublicId())
                : "群聊";
        String triggerBrief = trigger != null && StringUtils.hasText(trigger.getContent())
                ? trigger.getContent().trim()
                : "";
        if (triggerBrief.length() > 160) triggerBrief = triggerBrief.substring(0, 160) + "…";
        String replyBrief = result.trim();
        if (replyBrief.length() > 220) replyBrief = replyBrief.substring(0, 220) + "…";
        String ep = "在群「" + gName + "」参与过一轮对话。"
                + (StringUtils.hasText(triggerBrief) ? " 别人说：" + triggerBrief : "")
                + " 我回复：" + replyBrief;
        return ep.length() > EPISODE_MAX ? ep.substring(0, EPISODE_MAX) : ep;
    }

    private boolean isDuplicate(Long agentId, String content) {
        pruneDedup();
        String key = agentId + "|" + Integer.toHexString(content.trim().toLowerCase().hashCode());
        Long exp = recentClaims.get(key);
        return exp != null && exp > System.currentTimeMillis();
    }

    private void markSeen(Long agentId, String content) {
        String key = agentId + "|" + Integer.toHexString(content.trim().toLowerCase().hashCode());
        recentClaims.put(key, System.currentTimeMillis() + DEDUP_TTL_MS);
    }

    private void pruneDedup() {
        long now = System.currentTimeMillis();
        recentClaims.entrySet().removeIf(e -> e.getValue() < now);
    }

    private static void appendMemoryLines(StringBuilder sb, String label, Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) return;
        for (Object item : list) {
            String line = null;
            if (item instanceof Map<?, ?> m) {
                Object content = m.get("content");
                if (content != null) line = String.valueOf(content).trim();
            } else if (item != null) {
                line = String.valueOf(item).trim();
            }
            if (!StringUtils.hasText(line)) continue;
            sb.append("- ").append(label).append("：").append(line).append('\n');
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> slimList(Object raw, int max) {
        List<Object> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (out.size() >= max) break;
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> slim = new LinkedHashMap<>();
                    Object content = m.get("content");
                    if (content == null) content = m.get("claim");
                    if (content == null) content = m.get("text");
                    if (content != null) {
                        String s = String.valueOf(content);
                        if (s.length() > 280) s = s.substring(0, 280) + "…";
                        slim.put("content", s);
                    }
                    if (m.get("kind") != null) slim.put("kind", m.get("kind"));
                    out.add(slim);
                } else if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
        }
        return out;
    }
}
