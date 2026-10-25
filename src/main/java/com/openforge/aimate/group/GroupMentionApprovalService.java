package com.openforge.aimate.group;

import com.openforge.aimate.domain.ChatGroup;
import com.openforge.aimate.domain.GroupMember;
import com.openforge.aimate.domain.GroupMentionApproval;
import com.openforge.aimate.domain.GroupMessage;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.repository.ChatGroupRepository;
import com.openforge.aimate.repository.GroupMemberRepository;
import com.openforge.aimate.repository.GroupMentionApprovalRepository;
import com.openforge.aimate.repository.GroupMessageRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import com.openforge.aimate.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GroupMentionApprovalService {

    private final GroupMentionApprovalRepository approvalRepository;
    private final GroupMessageRepository messageRepository;
    private final GroupMemberRepository memberRepository;
    private final ChatGroupRepository groupRepository;
    private final UserAgentRepository userAgentRepository;
    private final UserRepository userRepository;
    private final GroupAccessService access;
    private final GroupEventPublisher eventPublisher;
    private final GroupMessageService messageService;
    private final GroupAgentDispatcher agentDispatcher;

    public GroupMentionApprovalService(
            GroupMentionApprovalRepository approvalRepository,
            GroupMessageRepository messageRepository,
            GroupMemberRepository memberRepository,
            ChatGroupRepository groupRepository,
            UserAgentRepository userAgentRepository,
            UserRepository userRepository,
            GroupAccessService access,
            GroupEventPublisher eventPublisher,
            @Lazy GroupMessageService messageService,
            @Lazy GroupAgentDispatcher agentDispatcher
    ) {
        this.approvalRepository = approvalRepository;
        this.messageRepository = messageRepository;
        this.memberRepository = memberRepository;
        this.groupRepository = groupRepository;
        this.userAgentRepository = userAgentRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.eventPublisher = eventPublisher;
        this.messageService = messageService;
        this.agentDispatcher = agentDispatcher;
    }

    /**
     * 被 @ 且开启请示时建卡；同一 trigger+target 已有卡则复用。
     * @return true 表示已建卡/已有待审，调用方应停止本轮开口
     */
    @Transactional
    public boolean requestApprovalIfNeeded(
            ChatGroup group,
            GroupMessage trigger,
            GroupMember targetAgentMember,
            UserAgent targetUa
    ) {
        if (group == null || trigger == null || targetAgentMember == null || targetUa == null) {
            return false;
        }
        if (!targetAgentMember.isRequireOwnerApprovalOnMention()) {
            return false;
        }
        // 主人本人点名：不请示
        if (trigger.getSenderType() == GroupMessage.SenderType.USER
                && Objects.equals(trigger.getSenderUserId(), targetUa.getUserId())) {
            return false;
        }

        var existing = approvalRepository.findByTriggerMessageIdAndTargetMemberId(
                trigger.getId(), targetAgentMember.getId());
        if (existing.isPresent()) {
            // 已有卡：本轮不重复开口（同意后由 decide → resume 开口）
            return true;
        }

        String agentName = StringUtils.hasText(targetUa.getName()) ? targetUa.getName() : "Agent";
        String requester = requesterLabel(trigger);
        String preview = clip(trigger.getContent(), 80);
        String content = requester + " 点名了「" + agentName + "」，等待主人确认"
                + (StringUtils.hasText(preview) ? "：" + preview : "");

        GroupMessage card = new GroupMessage();
        card.setGroupId(group.getId());
        card.setSenderType(GroupMessage.SenderType.SYSTEM);
        card.setContent(content);
        card.setReplyToId(trigger.getId());
        card.setMessageKind(GroupMessage.MessageKind.MENTION_APPROVAL);
        messageRepository.saveAndFlush(card);

        GroupMentionApproval row = new GroupMentionApproval();
        row.setGroupId(group.getId());
        row.setCardMessageId(card.getId());
        row.setTriggerMessageId(trigger.getId());
        row.setTargetMemberId(targetAgentMember.getId());
        row.setTargetUserAgentId(targetUa.getId());
        if (trigger.getSenderType() == GroupMessage.SenderType.USER) {
            row.setRequesterUserId(trigger.getSenderUserId());
        } else if (trigger.getSenderType() == GroupMessage.SenderType.AGENT) {
            row.setRequesterAgentId(trigger.getSenderAgentId());
        }
        row.setStatus(GroupMentionApproval.Status.PENDING);
        approvalRepository.saveAndFlush(row);

        groupRepository.touchActivity(group.getId());

        Map<String, Object> view = messageService.messageViewForPublish(card, null);
        eventPublisher.publish(group.getPublicId(), "MESSAGE", view);
        log.info("[MentionApproval] pending group={} agent={} trigger={}",
                group.getPublicId(), agentName, trigger.getId());
        return true;
    }

    @Transactional
    public Map<String, Object> decide(Long userId, String publicId, Long approvalId, String decisionRaw) {
        ChatGroup g = access.requireActiveGroup(publicId);
        access.requireUserMember(g, userId);
        GroupMentionApproval row = approvalRepository.findByIdAndGroupId(approvalId, g.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "审批不存在"));
        UserAgent targetUa = userAgentRepository.findById(row.getTargetUserAgentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent 不存在"));
        if (!Objects.equals(targetUa.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅 Agent 所属人类可审批");
        }
        if (row.getStatus() != GroupMentionApproval.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已处理过");
        }
        String d = decisionRaw != null ? decisionRaw.trim().toUpperCase() : "";
        boolean approve = "APPROVE".equals(d) || "APPROVED".equals(d);
        boolean reject = "REJECT".equals(d) || "REJECTED".equals(d);
        if (!approve && !reject) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision 须为 APPROVE 或 REJECT");
        }

        row.setStatus(approve ? GroupMentionApproval.Status.APPROVED : GroupMentionApproval.Status.REJECTED);
        row.setDecidedByUserId(userId);
        row.setDecidedAt(LocalDateTime.now());
        approvalRepository.save(row);

        GroupMessage card = messageRepository.findById(row.getCardMessageId()).orElse(null);
        if (card != null) {
            String agentName = StringUtils.hasText(targetUa.getName()) ? targetUa.getName() : "Agent";
            String ownerName = displayUser(userId);
            card.setContent(approve
                    ? ownerName + " 已同意「" + agentName + "」回应点名"
                    : ownerName + " 已拒绝「" + agentName + "」回应点名");
            messageRepository.save(card);
            Map<String, Object> view = messageService.messageViewForPublish(card, userId);
            eventPublisher.publish(g.getPublicId(), "MESSAGE", view);
        }

        if (approve) {
            GroupMessage trigger = messageRepository.findById(row.getTriggerMessageId()).orElse(null);
            GroupMember agentMember = memberRepository.findById(row.getTargetMemberId()).orElse(null);
            if (trigger != null && agentMember != null) {
                agentDispatcher.resumeAfterMentionApproved(g, trigger, agentMember);
            }
        }
        return Map.of(
                "ok", true,
                "status", row.getStatus().name(),
                "approvalId", row.getId()
        );
    }

    public Map<Long, GroupMentionApproval> byCardMessageIds(Collection<Long> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) return Map.of();
        return approvalRepository.findByCardMessageIdIn(cardIds).stream()
                .collect(Collectors.toMap(GroupMentionApproval::getCardMessageId, Function.identity(), (a, b) -> a));
    }

    public Map<String, Object> approvalView(GroupMentionApproval row, Long viewerUserId) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", row.getId());
        v.put("status", row.getStatus().name());
        v.put("triggerMessageId", row.getTriggerMessageId());
        v.put("targetMemberId", row.getTargetMemberId());
        v.put("targetUserAgentId", row.getTargetUserAgentId());
        v.put("requesterUserId", row.getRequesterUserId());
        v.put("requesterAgentId", row.getRequesterAgentId());
        v.put("decidedByUserId", row.getDecidedByUserId());
        v.put("decidedAt", row.getDecidedAt());

        userAgentRepository.findById(row.getTargetUserAgentId()).ifPresent(ua -> {
            v.put("targetAgentName", ua.getName());
            v.put("targetAgentAvatarUrl", ua.getAvatarUrl());
            v.put("ownerUserId", ua.getUserId());
            boolean canDecide = viewerUserId != null
                    && Objects.equals(ua.getUserId(), viewerUserId)
                    && row.getStatus() == GroupMentionApproval.Status.PENDING;
            v.put("canDecide", canDecide);
        });
        if (!v.containsKey("canDecide")) {
            v.put("canDecide", false);
        }

        v.put("requesterLabel", resolveRequesterLabel(row));
        messageRepository.findById(row.getTriggerMessageId()).ifPresent(t -> {
            v.put("triggerPreview", clip(t.getContent(), 120));
            v.put("triggerSenderType", t.getSenderType().name());
        });
        return v;
    }

    private String resolveRequesterLabel(GroupMentionApproval row) {
        if (row.getRequesterUserId() != null) {
            return displayUser(row.getRequesterUserId());
        }
        if (row.getRequesterAgentId() != null) {
            return userAgentRepository.findById(row.getRequesterAgentId())
                    .map(UserAgent::getName)
                    .filter(StringUtils::hasText)
                    .orElse("Agent");
        }
        return "某人";
    }

    private String requesterLabel(GroupMessage trigger) {
        if (trigger.getSenderType() == GroupMessage.SenderType.USER && trigger.getSenderUserId() != null) {
            return displayUser(trigger.getSenderUserId());
        }
        if (trigger.getSenderType() == GroupMessage.SenderType.AGENT && trigger.getSenderAgentId() != null) {
            return userAgentRepository.findById(trigger.getSenderAgentId())
                    .map(UserAgent::getName)
                    .filter(StringUtils::hasText)
                    .orElse("Agent");
        }
        return "某人";
    }

    private String displayUser(Long userId) {
        if (userId == null) return "用户";
        return userRepository.findById(userId)
                .map(u -> StringUtils.hasText(u.getDisplayName()) ? u.getDisplayName() : u.getUsername())
                .orElse("用户#" + userId);
    }

    private static String clip(String s, int n) {
        if (!StringUtils.hasText(s)) return "";
        String t = s.trim().replace('\n', ' ');
        return t.length() <= n ? t : t.substring(0, n) + "…";
    }
}
