package com.openforge.aimate.group;

import com.openforge.aimate.domain.AgentExecutionMode;
import com.openforge.aimate.domain.ChatGroup;
import com.openforge.aimate.domain.GroupMember;
import com.openforge.aimate.domain.GroupMessage;
import com.openforge.aimate.domain.LocalBridgeTask;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.repository.ChatGroupRepository;
import com.openforge.aimate.repository.GroupMemberRepository;
import com.openforge.aimate.repository.GroupMessageRepository;
import com.openforge.aimate.repository.LocalBridgeTaskRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import com.openforge.aimate.repository.UserRepository;
import com.openforge.aimate.storage.ChatCosStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupChatService {

    private final ChatGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final UserAgentRepository userAgentRepository;
    private final LocalBridgeTaskRepository localBridgeTaskRepository;
    private final GroupAccessService access;
    private final GroupEventPublisher eventPublisher;
    private final ChatCosStorageService chatCos;

    @Transactional
    public Map<String, Object> create(Long userId, CreateGroupRequest req) {
        if (!StringUtils.hasText(req.title())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写群名称");
        }
        ChatGroup g = new ChatGroup();
        g.setPublicId("g_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        g.setTitle(req.title().trim());
        // 建群事务内绝不碰 COS：头像由前端创建成功后再 PATCH（失败也不影响群本身）
        g.setAvatarUrl(null);
        g.setOwnerUserId(userId);
        g.setCreatedBy(userId);
        g.setStatus(ChatGroup.Status.ACTIVE);
        g.setInviteCode(newInviteCode());
        // IDENTITY 需 flush 后才有 id；必须接住返回值（version 非 null 时会走 merge，原引用可能无 id）
        ChatGroup saved = groupRepository.saveAndFlush(g);
        if (saved.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建群组失败：未生成 ID");
        }

        GroupMember owner = newUserMember(saved.getId(), userId, GroupMember.Role.OWNER);
        memberRepository.save(owner);

        List<Long> addedUsers = new ArrayList<>();
        List<Long> addedAgents = new ArrayList<>();
        if (req.userIds() != null) {
            for (Long uid : req.userIds()) {
                if (uid == null || uid.equals(userId)) continue;
                if (!userRepository.existsById(uid)) continue;
                if (memberRepository.existsByGroupIdAndUserId(saved.getId(), uid)) continue;
                memberRepository.save(newUserMember(saved.getId(), uid, GroupMember.Role.MEMBER));
                addedUsers.add(uid);
            }
        }
        if (req.agentIds() != null) {
            for (Long aid : req.agentIds()) {
                addAgentInternal(saved, userId, aid, defaultReplyMode(aid), addedAgents);
            }
        }
        if (Boolean.TRUE.equals(req.includeSecretary())) {
            userAgentRepository.findByUserIdAndKind(userId, UserAgent.Kind.SECRETARY)
                    .filter(ua -> ua.getStatus() == UserAgent.Status.ACTIVE)
                    .ifPresent(ua -> addAgentInternal(saved, userId, ua.getId(),
                            GroupMember.AgentReplyMode.SMART, addedAgents));
        }

        postSystem(saved, "创建了群「" + saved.getTitle() + "」", userId);
        for (Long uid : addedUsers) {
            User u = userRepository.findById(uid).orElse(null);
            postSystem(saved, (u != null ? display(u) : ("用户#" + uid)) + " 加入了群聊", userId);
        }
        for (Long aid : addedAgents) {
            UserAgent ua = userAgentRepository.findById(aid).orElse(null);
            postSystem(saved, (ua != null ? ua.getName() : ("Agent#" + aid)) + " 加入了群聊", userId);
        }

        Map<String, Object> view = groupView(saved, userId);
        view.put("suggestedSecretaryAgentId", suggestedSecretaryId(userId));
        return view;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMine(Long userId) {
        return groupRepository.findActiveForUser(userId).stream()
                .map(g -> groupView(g, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long userId, String publicId) {
        ChatGroup g = access.requireActiveGroup(publicId);
        access.requireUserMember(g, userId);
        Map<String, Object> view = groupView(g, userId);
        view.put("members", membersView(g.getId()));
        view.put("suggestedSecretaryAgentId", suggestedSecretaryId(userId));
        return view;
    }

    @Transactional
    public Map<String, Object> patch(Long userId, String publicId, PatchGroupRequest req) {
        ChatGroup g = access.requireActiveGroup(publicId);
        GroupMember me = access.requireUserMember(g, userId);
        boolean dirtyGroup = false;
        if (StringUtils.hasText(req.title()) || req.avatarUrl() != null || req.announcement() != null
                || req.membersInviteEnabled() != null) {
            access.requireOwnerOrAdmin(me);
        }
        if (StringUtils.hasText(req.title())) {
            String old = g.getTitle();
            g.setTitle(req.title().trim());
            postSystem(g, displayUser(userId) + " 将群名改为「" + g.getTitle() + "」（原「" + old + "」）", userId);
            dirtyGroup = true;
        }
        if (req.avatarUrl() != null) {
            g.setAvatarUrl(req.avatarUrl().isBlank() ? null : resolveAvatar(req.avatarUrl()));
            dirtyGroup = true;
        }
        if (req.announcement() != null) {
            g.setAnnouncement(trim(req.announcement(), 4000));
            postSystem(g, displayUser(userId) + " 更新了群公告", userId);
            dirtyGroup = true;
        }
        if (req.membersInviteEnabled() != null) {
            g.setMembersInviteEnabled(req.membersInviteEnabled());
            postSystem(g, displayUser(userId) + (req.membersInviteEnabled()
                    ? " 开启了成员邀请"
                    : " 关闭了成员邀请（仅群主/管理员可邀请）"), userId);
            dirtyGroup = true;
        }
        if (dirtyGroup) {
            groupRepository.save(g);
        }
        // 个人：备注 / 本群昵称（任意成员可改自己的）
        boolean dirtyMe = false;
        if (req.remark() != null) {
            me.setRemark(trim(req.remark(), 128));
            dirtyMe = true;
        }
        if (req.nickname() != null) {
            me.setNickname(trim(req.nickname(), 64));
            dirtyMe = true;
        }
        if (dirtyMe) {
            memberRepository.save(me);
        }
        eventPublisher.publish(g.getPublicId(), "MEMBER", Map.of("action", "updated"));
        return groupView(g, userId);
    }

    @Transactional
    public Map<String, Object> setPinned(Long userId, String publicId, boolean pinned) {
        ChatGroup g = access.requireActiveGroup(publicId);
        GroupMember me = access.requireUserMember(g, userId);
        me.setPinnedAt(pinned ? LocalDateTime.now() : null);
        memberRepository.save(me);
        return groupView(g, userId);
    }

    @Transactional
    public Map<String, Object> setArchived(Long userId, String publicId, boolean archived) {
        ChatGroup g = access.requireActiveGroup(publicId);
        GroupMember me = access.requireUserMember(g, userId);
        me.setArchived(archived);
        if (archived) me.setPinnedAt(null);
        memberRepository.save(me);
        return Map.of("ok", true, "archived", archived, "publicId", publicId);
    }

    @Transactional
    public Map<String, Object> inviteInfo(Long userId, String publicId) {
        ChatGroup g = access.requireActiveGroup(publicId);
        access.requireUserMember(g, userId);
        ensureInviteCode(g);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("publicId", g.getPublicId());
        m.put("title", g.getTitle());
        m.put("inviteCode", g.getInviteCode());
        m.put("invitePath", "/groups/join/" + g.getInviteCode());
        return m;
    }

    @Transactional
    public Map<String, Object> refreshInvite(Long userId, String publicId) {
        ChatGroup g = access.requireActiveGroup(publicId);
        GroupMember me = access.requireUserMember(g, userId);
        access.requireOwnerOrAdmin(me);
        g.setInviteCode(newInviteCode());
        groupRepository.save(g);
        return inviteInfo(userId, publicId);
    }

    @Transactional
    public Map<String, Object> joinByInvite(Long userId, String inviteCode) {
        if (!StringUtils.hasText(inviteCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少邀请码");
        }
        ChatGroup g = groupRepository.findByInviteCode(inviteCode.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "邀请无效或已失效"));
        if (g.getStatus() != ChatGroup.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.GONE, "群组已解散");
        }
        if (memberRepository.existsByGroupIdAndUserId(g.getId(), userId)) {
            return groupView(g, userId);
        }
        if (!g.isMembersInviteEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该群已关闭邀请入群");
        }
        memberRepository.save(newUserMember(g.getId(), userId, GroupMember.Role.MEMBER));
        postSystem(g, displayUser(userId) + " 通过扫码加入了群聊", userId);
        eventPublisher.publish(g.getPublicId(), "MEMBER", Map.of("action", "joined", "userId", userId));
        return groupView(g, userId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewInvite(String inviteCode) {
        ChatGroup g = groupRepository.findByInviteCode(inviteCode == null ? "" : inviteCode.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "邀请无效或已失效"));
        if (g.getStatus() != ChatGroup.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.GONE, "群组已解散");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("publicId", g.getPublicId());
        m.put("title", g.getTitle());
        m.put("avatarUrl", g.getAvatarUrl());
        m.put("memberCount", memberRepository.findByGroupIdOrderByIdAsc(g.getId()).size());
        return m;
    }

    private void ensureInviteCode(ChatGroup g) {
        if (StringUtils.hasText(g.getInviteCode())) return;
        g.setInviteCode(newInviteCode());
        groupRepository.save(g);
    }

    private String newInviteCode() {
        for (int i = 0; i < 8; i++) {
            String code = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            if (groupRepository.findByInviteCode(code).isEmpty()) return code;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Transactional
    public Map<String, Object> addMembers(Long userId, String publicId, AddMembersRequest req) {
        ChatGroup g = access.requireActiveGroup(publicId);
        GroupMember me = access.requireUserMember(g, userId);
        requireCanInvite(g, me);
        List<Map<String, Object>> added = new ArrayList<>();
        if (req.userIds() != null) {
            for (Long uid : req.userIds()) {
                if (uid == null || memberRepository.existsByGroupIdAndUserId(g.getId(), uid)) continue;
                if (!userRepository.existsById(uid)) continue;
                GroupMember m = memberRepository.save(newUserMember(g.getId(), uid, GroupMember.Role.MEMBER));
                postSystem(g, displayUser(uid) + " 加入了群聊", userId);
                added.add(memberView(m));
            }
        }
        if (req.agentIds() != null) {
            for (Long aid : req.agentIds()) {
                GroupMember.AgentReplyMode mode = req.replyMode() != null
                        ? parseMode(req.replyMode())
                        : defaultReplyMode(aid);
                List<Long> box = new ArrayList<>();
                addAgentInternal(g, userId, aid, mode, box);
                if (!box.isEmpty()) {
                    UserAgent ua = userAgentRepository.findById(aid).orElse(null);
                    postSystem(g, (ua != null ? ua.getName() : ("Agent#" + aid)) + " 加入了群聊", userId);
                    memberRepository.findByGroupIdAndUserAgentId(g.getId(), aid)
                            .ifPresent(m -> added.add(memberView(m)));
                }
            }
        }
        if (Boolean.TRUE.equals(req.includeSecretary())) {
            userAgentRepository.findByUserIdAndKind(userId, UserAgent.Kind.SECRETARY)
                    .filter(ua -> ua.getStatus() == UserAgent.Status.ACTIVE)
                    .ifPresent(ua -> {
                        List<Long> box = new ArrayList<>();
                        addAgentInternal(g, userId, ua.getId(), GroupMember.AgentReplyMode.SMART, box);
                        if (!box.isEmpty()) {
                            postSystem(g, ua.getName() + " 加入了群聊", userId);
                            memberRepository.findByGroupIdAndUserAgentId(g.getId(), ua.getId())
                                    .ifPresent(m -> added.add(memberView(m)));
                        }
                    });
        }
        eventPublisher.publish(g.getPublicId(), "MEMBER", Map.of("action", "added", "count", added.size()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("added", added);
        out.put("suggestSecretary", suggestedSecretaryId(userId));
        out.put("members", membersView(g.getId()));
        return out;
    }

    @Transactional
    public void removeMember(Long userId, String publicId, Long memberId) {
        ChatGroup g = access.requireActiveGroup(publicId);
        GroupMember me = access.requireUserMember(g, userId);
        GroupMember target = memberRepository.findByIdAndGroupId(memberId, g.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "成员不存在"));

        boolean selfLeave = target.getMemberType() == GroupMember.MemberType.USER
                && Objects.equals(target.getUserId(), userId);
        boolean ownAgent = target.getMemberType() == GroupMember.MemberType.AGENT
                && target.getUserAgentId() != null
                && userAgentRepository.findById(target.getUserAgentId())
                .map(ua -> Objects.equals(ua.getUserId(), userId))
                .orElse(false);

        if (!selfLeave && !ownAgent) {
            access.requireOwnerOrAdmin(me);
            if (target.getRole() == GroupMember.Role.OWNER) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能移除群主");
            }
            if (target.getRole() == GroupMember.Role.ADMIN && me.getRole() != GroupMember.Role.OWNER) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅群主可移除管理员");
            }
        }
        if (target.getRole() == GroupMember.Role.OWNER && selfLeave) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "群主请先解散群或转让后再退群");
        }
        // 自己的 Agent 若是管理员，普通成员不可擅自移出（需群主）
        if (ownAgent && !selfLeave
                && target.getRole() == GroupMember.Role.ADMIN
                && me.getRole() != GroupMember.Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅群主可移除管理员 Agent");
        }
        String label = memberLabel(target);
        memberRepository.delete(target);
        postSystem(g, label + (selfLeave ? " 退出了群聊" : " 被移出群聊"), userId);
        eventPublisher.publish(g.getPublicId(), "MEMBER", Map.of("action", "removed", "memberId", memberId));
    }

    @Transactional
    public Map<String, Object> setAdmin(Long userId, String publicId, Long memberId, boolean promote) {
        ChatGroup g = access.requireActiveGroup(publicId);
        GroupMember me = access.requireUserMember(g, userId);
        access.requireOwner(me);
        GroupMember target = memberRepository.findByIdAndGroupId(memberId, g.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "成员不存在"));
        // 人类与 Agent 一视同仁，均可设为管理员
        if (target.getRole() == GroupMember.Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "群主角色不可更改");
        }
        target.setRole(promote ? GroupMember.Role.ADMIN : GroupMember.Role.MEMBER);
        memberRepository.save(target);
        postSystem(g, memberLabel(target) + (promote ? " 被设为管理员" : " 被取消管理员"), userId);
        eventPublisher.publish(g.getPublicId(), "MEMBER", Map.of("action", promote ? "promote" : "demote", "memberId", memberId));
        return memberView(target);
    }

    @Transactional
    public Map<String, Object> setReplyModes(Long userId, String publicId, ReplyModeRequest req) {
        ChatGroup g = access.requireActiveGroup(publicId);
        GroupMember me = access.requireUserMember(g, userId);
        GroupMember.AgentReplyMode mode = parseMode(req.mode());
        List<Long> ids = req.agentMemberIds() != null ? req.agentMemberIds() : List.of();
        if (ids.isEmpty() && req.agentMemberId() != null) {
            ids = List.of(req.agentMemberId());
        }
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请指定 Agent 成员");
        }
        List<Map<String, Object>> updated = new ArrayList<>();
        for (Long mid : ids) {
            GroupMember target = memberRepository.findByIdAndGroupId(mid, g.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "成员不存在: " + mid));
            if (target.getMemberType() != GroupMember.MemberType.AGENT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅 Agent 可设置回复模式");
            }
            UserAgent ua = userAgentRepository.findById(target.getUserAgentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent 不存在"));
            boolean owns = Objects.equals(ua.getUserId(), userId);
            if (!owns) {
                access.requireOwnerOrAdmin(me);
            }
            if (ua.getExecutionMode() == AgentExecutionMode.LOCAL_BRIDGE
                    && mode != GroupMember.AgentReplyMode.MENTION_ONLY
                    && mode != GroupMember.AgentReplyMode.SILENT) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "本地 AI Agent 仅支持「仅 @」或「静默」"
                );
            }
            target.setAgentReplyMode(mode);
            memberRepository.save(target);
            updated.add(memberView(target));
        }
        postSystem(g, displayUser(userId) + " 将 " + updated.size() + " 个 Agent 回复模式设为 " + mode.name(), userId);
        eventPublisher.publish(g.getPublicId(), "MEMBER", Map.of("action", "reply_mode", "count", updated.size()));
        return Map.of("updated", updated);
    }

    @Transactional
    public void dissolve(Long userId, String publicId) {
        ChatGroup g = access.requireActiveGroup(publicId);
        GroupMember me = access.requireUserMember(g, userId);
        access.requireOwner(me);
        g.setStatus(ChatGroup.Status.DISSOLVED);
        groupRepository.save(g);
        postSystem(g, "群主解散了群聊", userId);
        eventPublisher.publish(g.getPublicId(), "MEMBER", Map.of("action", "dissolved"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> searchUsers(Long userId, String q, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size <= 0 ? 20 : size));
        Page<User> result = userRepository.search(q == null ? "" : q.trim(), PageRequest.of(p, s));
        List<Map<String, Object>> items = result.getContent().stream()
                .filter(u -> !Objects.equals(u.getId(), userId))
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("displayName", u.getDisplayName());
                    m.put("email", u.getEmail());
                    m.put("avatarUrl", u.getAvatarUrl());
                    return m;
                })
                .toList();
        return Map.of(
                "items", items,
                "page", p,
                "size", s,
                "total", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        );
    }

    private void addAgentInternal(ChatGroup g, Long actorUserId, Long agentId,
                                  GroupMember.AgentReplyMode mode, List<Long> addedOut) {
        if (agentId == null) return;
        if (memberRepository.findByGroupIdAndUserAgentId(g.getId(), agentId).isPresent()) return;
        UserAgent ua = userAgentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent 不存在"));
        if (ua.getStatus() != UserAgent.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent 不可用: " + ua.getName());
        }
        // 只能拉自己的 Agent（群主/管理员也可代拉？一期：仅本人 Agent）
        if (!Objects.equals(ua.getUserId(), actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能邀请自己的 Agent");
        }
        GroupMember m = new GroupMember();
        m.setGroupId(g.getId());
        m.setMemberType(GroupMember.MemberType.AGENT);
        m.setUserAgentId(agentId);
        m.setRole(GroupMember.Role.MEMBER);
        m.setAgentReplyMode(mode != null ? mode : defaultReplyMode(agentId));
        m.setJoinedAt(LocalDateTime.now());
        memberRepository.save(m);
        addedOut.add(agentId);
    }

    private GroupMember.AgentReplyMode defaultReplyMode(Long agentId) {
        return userAgentRepository.findById(agentId)
                .map(ua -> ua.getKind() == UserAgent.Kind.SECRETARY
                        ? GroupMember.AgentReplyMode.SMART
                        : GroupMember.AgentReplyMode.MENTION_ONLY)
                .orElse(GroupMember.AgentReplyMode.MENTION_ONLY);
    }

    private GroupMember.AgentReplyMode parseMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请指定回复模式");
        }
        try {
            return GroupMember.AgentReplyMode.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效回复模式: " + raw);
        }
    }

    private GroupMember newUserMember(Long groupId, Long userId, GroupMember.Role role) {
        GroupMember m = new GroupMember();
        m.setGroupId(groupId);
        m.setMemberType(GroupMember.MemberType.USER);
        m.setUserId(userId);
        m.setRole(role);
        m.setJoinedAt(LocalDateTime.now());
        return m;
    }

    private void postSystem(ChatGroup g, String content, Long actorUserId) {
        GroupMessage msg = new GroupMessage();
        msg.setGroupId(g.getId());
        msg.setSenderType(GroupMessage.SenderType.SYSTEM);
        msg.setSenderUserId(actorUserId);
        msg.setContent(content);
        messageRepository.saveAndFlush(msg);
        g.setUpdateTime(LocalDateTime.now());
        groupRepository.save(g);
        eventPublisher.publish(g.getPublicId(), "MESSAGE", Map.of(
                "id", msg.getId(),
                "senderType", "SYSTEM",
                "content", content,
                "createTime", msg.getCreateTime() != null ? msg.getCreateTime().toString() : LocalDateTime.now().toString()
        ));
    }

    private Long suggestedSecretaryId(Long userId) {
        return userAgentRepository.findByUserIdAndKind(userId, UserAgent.Kind.SECRETARY)
                .filter(ua -> ua.getStatus() == UserAgent.Status.ACTIVE)
                .map(UserAgent::getId)
                .orElse(null);
    }

    private Map<String, Object> groupView(ChatGroup g, Long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("publicId", g.getPublicId());
        m.put("title", g.getTitle());
        m.put("avatarUrl", g.getAvatarUrl());
        m.put("announcement", g.getAnnouncement());
        m.put("inviteCode", g.getInviteCode());
        m.put("membersInviteEnabled", g.isMembersInviteEnabled());
        m.put("ownerUserId", g.getOwnerUserId());
        m.put("status", g.getStatus().name());
        m.put("createTime", g.getCreateTime());
        m.put("updateTime", g.getUpdateTime());
        memberRepository.findByGroupIdAndUserId(g.getId(), userId).ifPresent(me -> {
            m.put("myRole", me.getRole().name());
            m.put("myMemberId", me.getId());
            m.put("myNickname", me.getNickname());
            m.put("myRemark", me.getRemark());
            boolean canInvite = me.getRole() == GroupMember.Role.OWNER
                    || me.getRole() == GroupMember.Role.ADMIN
                    || g.isMembersInviteEnabled();
            m.put("canInvite", canInvite);
            m.put("pinned", me.getPinnedAt() != null);
            m.put("archived", me.isArchived());
            // 列表显示名：有备注用备注
            m.put("displayTitle", StringUtils.hasText(me.getRemark()) ? me.getRemark() : g.getTitle());
        });
        List<GroupMember> members = memberRepository.findByGroupIdOrderByIdAsc(g.getId());
        m.put("memberCount", members.size());
        m.put("wsSubscribePath", "/topic/group." + g.getPublicId());
        return m;
    }

    private List<Map<String, Object>> membersView(Long groupId) {
        return memberRepository.findByGroupIdOrderByIdAsc(groupId).stream()
                .map(this::memberView)
                .toList();
    }

    private Map<String, Object> memberView(GroupMember m) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.getId());
        v.put("memberType", m.getMemberType().name());
        v.put("role", m.getRole().name());
        v.put("joinedAt", m.getJoinedAt());
        if (m.getMemberType() == GroupMember.MemberType.USER) {
            v.put("userId", m.getUserId());
            v.put("nickname", m.getNickname());
            userRepository.findById(m.getUserId()).ifPresent(u -> {
                String base = display(u);
                v.put("name", StringUtils.hasText(m.getNickname()) ? m.getNickname() : base);
                v.put("username", u.getUsername());
                v.put("avatarUrl", u.getAvatarUrl());
            });
        } else {
            v.put("userAgentId", m.getUserAgentId());
            v.put("agentReplyMode", m.getAgentReplyMode() != null ? m.getAgentReplyMode().name() : null);
            userAgentRepository.findById(m.getUserAgentId()).ifPresent(ua -> {
                v.put("name", ua.getName());
                v.put("avatarUrl", ua.getAvatarUrl());
                v.put("agentKind", ua.getKind().name());
                v.put("ownerUserId", ua.getUserId());
                v.put("ownerName", displayUser(ua.getUserId()));
                v.put("executionMode", ua.getExecutionMode() != null
                        ? ua.getExecutionMode().name()
                        : AgentExecutionMode.CLOUD_LLM.name());
                boolean localBridge = ua.getExecutionMode() == AgentExecutionMode.LOCAL_BRIDGE;
                v.put("localBridge", localBridge);
                if (localBridge && m.getGroupId() != null) {
                    v.put("taskCounts", taskCounts(m.getGroupId(), ua.getId()));
                }
            });
        }
        return v;
    }

    /** 群内某 Agent 的 Local Bridge 任务池（分页） */
    @Transactional(readOnly = true)
    public Map<String, Object> listAgentTasks(
            Long userId,
            String publicId,
            Long userAgentId,
            String statusRaw,
            int page,
            int size
    ) {
        ChatGroup g = access.requireActiveGroup(publicId);
        GroupMember me = access.requireUserMember(g, userId);
        GroupMember agentMember = memberRepository.findByGroupIdAndUserAgentId(g.getId(), userAgentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "该 Agent 不在本群"));
        UserAgent ua = userAgentRepository.findById(userAgentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent 不存在"));
        boolean owns = Objects.equals(ua.getUserId(), userId);
        boolean manage = me.getRole() == GroupMember.Role.OWNER || me.getRole() == GroupMember.Role.ADMIN;
        if (!owns && !manage) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅 Agent 主人或群管理可查看任务池");
        }
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        PageRequest pr = PageRequest.of(p, s);
        Page<LocalBridgeTask> result;
        if (StringUtils.hasText(statusRaw)) {
            LocalBridgeTask.Status st;
            try {
                st = LocalBridgeTask.Status.valueOf(statusRaw.trim().toUpperCase());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效状态: " + statusRaw);
            }
            result = localBridgeTaskRepository.findByGroupIdAndUserAgentIdAndStatusOrderByIdDesc(
                    g.getId(), userAgentId, st, pr);
        } else {
            result = localBridgeTaskRepository.findByGroupIdAndUserAgentIdOrderByIdDesc(
                    g.getId(), userAgentId, pr);
        }
        List<Map<String, Object>> items = result.getContent().stream().map(this::taskView).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("page", p);
        out.put("size", s);
        out.put("total", result.getTotalElements());
        out.put("totalPages", result.getTotalPages());
        out.put("counts", taskCounts(g.getId(), userAgentId));
        out.put("memberId", agentMember.getId());
        out.put("userAgentId", userAgentId);
        out.put("agentName", ua.getName());
        return out;
    }

    private Map<String, Object> taskCounts(Long groupId, Long userAgentId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LocalBridgeTask.Status st : LocalBridgeTask.Status.values()) {
            counts.put(st.name(), 0L);
        }
        for (Object[] row : localBridgeTaskRepository.countByStatusGrouped(groupId, userAgentId)) {
            if (row == null || row.length < 2 || row[0] == null) continue;
            String key = row[0] instanceof LocalBridgeTask.Status s ? s.name() : String.valueOf(row[0]);
            long n = row[1] instanceof Number num ? num.longValue() : 0L;
            counts.put(key, n);
        }
        long open = counts.getOrDefault("PENDING", 0L) + counts.getOrDefault("CLAIMED", 0L);
        Map<String, Object> m = new LinkedHashMap<>(counts);
        m.put("OPEN", open);
        m.put("TOTAL", counts.values().stream().mapToLong(Long::longValue).sum());
        return m;
    }

    private Map<String, Object> taskView(LocalBridgeTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("publicId", t.getPublicId());
        m.put("status", t.getStatus().name());
        m.put("statusLabel", taskStatusLabel(t.getStatus()));
        String instruction = t.getInstruction();
        if (instruction != null && instruction.length() > 200) {
            instruction = instruction.substring(0, 200) + "…";
        }
        m.put("instruction", instruction);
        m.put("errorMessage", t.getErrorMessage());
        m.put("triggerMessageId", t.getTriggerMessageId());
        m.put("claimedAt", t.getClaimedAt());
        m.put("completedAt", t.getCompletedAt());
        m.put("createTime", t.getCreateTime());
        boolean hasResult = StringUtils.hasText(t.getResultText());
        m.put("hasResult", hasResult);
        if (hasResult) {
            String r = t.getResultText();
            m.put("resultPreview", r.length() > 160 ? r.substring(0, 160) + "…" : r);
        }
        return m;
    }

    private static String taskStatusLabel(LocalBridgeTask.Status st) {
        return switch (st) {
            case PENDING -> "未领取";
            case CLAIMED -> "已领取";
            case DONE -> "已完成";
            case FAILED -> "失败";
            case CANCELLED -> "已取消";
        };
    }

    private void requireCanInvite(ChatGroup g, GroupMember me) {
        if (me.getRole() == GroupMember.Role.OWNER || me.getRole() == GroupMember.Role.ADMIN) return;
        if (!g.isMembersInviteEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "群主已关闭成员邀请，请联系管理员");
        }
    }

    private String memberLabel(GroupMember m) {
        if (m.getMemberType() == GroupMember.MemberType.USER) {
            return displayUser(m.getUserId());
        }
        return userAgentRepository.findById(m.getUserAgentId())
                .map(UserAgent::getName)
                .orElse("Agent#" + m.getUserAgentId());
    }

    private String displayUser(Long userId) {
        return userRepository.findById(userId).map(this::display).orElse("用户#" + userId);
    }

    private String display(User u) {
        if (StringUtils.hasText(u.getDisplayName())) return u.getDisplayName();
        return u.getUsername();
    }

    /**
     * 群头像：data URL 落 COS；COS 失败时跳过，绝不拖垮建群（否则 COS 401 会被前端当成登录失效）。
     */
    private String resolveAvatar(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String t = raw.trim();
        try {
            if (t.startsWith("data:")) {
                if (!chatCos.isUsable()) {
                    log.warn("[Group] 群头像为 data URL 但 COS 未配置，已跳过");
                    return null;
                }
                List<String> urls = chatCos.persistUserImages(List.of(t));
                if (!urls.isEmpty()) return trim(urls.get(0), 512);
                return null;
            }
            if (t.startsWith("http://") || t.startsWith("https://")) {
                return trim(t, 512);
            }
            return null;
        } catch (Exception e) {
            log.warn("[Group] 群头像上传失败，跳过继续建群: {}", e.getMessage());
            return null;
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    public record CreateGroupRequest(
            String title,
            String avatarUrl,
            List<Long> userIds,
            List<Long> agentIds,
            Boolean includeSecretary
    ) {}

    public record PatchGroupRequest(
            String title,
            String avatarUrl,
            String announcement,
            String remark,
            String nickname,
            Boolean membersInviteEnabled
    ) {}

    public record AddMembersRequest(
            List<Long> userIds,
            List<Long> agentIds,
            Boolean includeSecretary,
            String replyMode
    ) {}

    public record ReplyModeRequest(List<Long> agentMemberIds, Long agentMemberId, String mode) {}
}
