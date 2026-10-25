package com.openforge.aimate.group;

import com.openforge.aimate.agent.UserAgentIdentityService;
import com.openforge.aimate.domain.ChatGroup;
import com.openforge.aimate.domain.GroupAttachment;
import com.openforge.aimate.domain.GroupMember;
import com.openforge.aimate.domain.GroupMentionApproval;
import com.openforge.aimate.domain.GroupMessage;
import com.openforge.aimate.domain.GroupMessageRead;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.repository.ChatGroupRepository;
import com.openforge.aimate.repository.GroupAttachmentRepository;
import com.openforge.aimate.repository.GroupMemberRepository;
import com.openforge.aimate.repository.GroupMessageReadRepository;
import com.openforge.aimate.repository.GroupMessageRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import com.openforge.aimate.repository.UserRepository;
import com.openforge.aimate.memory.GroupMemoryCaptureService;
import com.openforge.aimate.storage.ChatCosStorageService;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class GroupMessageService {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);

    private final ChatGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupMessageRepository messageRepository;
    private final GroupAttachmentRepository attachmentRepository;
    private final GroupMessageReadRepository readRepository;
    private final UserRepository userRepository;
    private final UserAgentRepository userAgentRepository;
    private final UserAgentIdentityService identityService;
    private final GroupAccessService access;
    private final GroupEventPublisher eventPublisher;
    private final ChatCosStorageService chatCos;
    private final GroupAgentDispatcher agentDispatcher;
    private final GroupMentionApprovalService mentionApprovalService;
    private final GroupMemoryCaptureService groupMemoryCaptureService;

    public GroupMessageService(
            ChatGroupRepository groupRepository,
            GroupMemberRepository memberRepository,
            GroupMessageRepository messageRepository,
            GroupAttachmentRepository attachmentRepository,
            GroupMessageReadRepository readRepository,
            UserRepository userRepository,
            UserAgentRepository userAgentRepository,
            UserAgentIdentityService identityService,
            GroupAccessService access,
            GroupEventPublisher eventPublisher,
            ChatCosStorageService chatCos,
            @Lazy GroupAgentDispatcher agentDispatcher,
            @Lazy GroupMentionApprovalService mentionApprovalService,
            GroupMemoryCaptureService groupMemoryCaptureService
    ) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
        this.readRepository = readRepository;
        this.userRepository = userRepository;
        this.userAgentRepository = userAgentRepository;
        this.identityService = identityService;
        this.access = access;
        this.eventPublisher = eventPublisher;
        this.chatCos = chatCos;
        this.agentDispatcher = agentDispatcher;
        this.mentionApprovalService = mentionApprovalService;
        this.groupMemoryCaptureService = groupMemoryCaptureService;
    }

    /** SSE / 建卡发布用：组装单条消息 view（含审批卡）。 */
    public Map<String, Object> messageViewForPublish(GroupMessage msg, Long viewerUserId) {
        return messageView(msg, List.of(), List.of(), Map.of(), Map.of(), viewerUserId,
                mentionApprovalService.byCardMessageIds(
                        msg.getMessageKind() == GroupMessage.MessageKind.MENTION_APPROVAL
                                ? List.of(msg.getId()) : List.of()));
    }

    @Transactional
    public Map<String, Object> send(Long userId, String publicId, SendRequest req) {
        ChatGroup g = access.requireActiveGroup(publicId);
        GroupMember me = access.requireUserMember(g, userId);
        String content = req.content() != null ? req.content().trim() : "";
        List<AttachmentIn> atts = req.attachments() != null ? req.attachments() : List.of();
        if (content.isEmpty() && atts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入内容或附件");
        }
        if (req.clientMsgId() != null && !req.clientMsgId().isBlank()) {
            // idempotent soft check skipped for brevity
        }

        GroupMessage msg = new GroupMessage();
        msg.setGroupId(g.getId());
        msg.setSenderType(GroupMessage.SenderType.USER);
        msg.setSenderUserId(userId);
        msg.setContent(content.isEmpty() ? null : content);
        msg.setReplyToId(req.replyToId());
        msg.setClientMsgId(trim(req.clientMsgId(), 64));
        messageRepository.saveAndFlush(msg);

        List<GroupAttachment> savedAtts = persistAttachments(g.getId(), msg.getId(), atts, content);
        extractLinks(g.getId(), msg.getId(), content);

        markRead(msg, me, 0L);

        groupRepository.touchActivity(g.getId());

        Map<String, Object> view = messageView(msg, savedAtts, List.of(readBrief(me, 0L)), userId);
        eventPublisher.publish(g.getPublicId(), "MESSAGE", view);

        // 事务提交后再派发，避免异步线程读不到本条消息
        final ChatGroup groupRef = g;
        final GroupMessage msgRef = msg;
        final GroupMember senderRef = me;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    agentDispatcher.dispatchAfterUserMessage(groupRef, msgRef, senderRef);
                    groupMemoryCaptureService.observeImportantUserMessage(groupRef.getId(), msgRef.getId());
                }
            });
        } else {
            agentDispatcher.dispatchAfterUserMessage(groupRef, msgRef, senderRef);
            groupMemoryCaptureService.observeImportantUserMessage(groupRef.getId(), msgRef.getId());
        }

        return view;
    }

    /** 分享快照：按时间正序取最近 limit 条（已 enrich）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> snapshotForShare(Long groupId, int limit) {
        int lim = Math.min(500, Math.max(1, limit <= 0 ? 200 : limit));
        List<GroupMessage> msgs = messageRepository.findByGroupIdOrderByIdDesc(groupId, PageRequest.of(0, lim));
        msgs = new ArrayList<>(msgs);
        java.util.Collections.reverse(msgs);
        return enrichMessages(msgs, null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMessages(Long userId, String publicId, Long beforeId, String onDay, int limit) {
        ChatGroup g = access.requireActiveGroup(publicId);
        access.requireUserMember(g, userId);
        int lim = Math.min(100, Math.max(1, limit <= 0 ? 50 : limit));

        List<GroupMessage> msgs;
        if (StringUtils.hasText(onDay)) {
            LocalDate day = LocalDate.parse(onDay.trim());
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.plusDays(1).atStartOfDay();
            msgs = messageRepository.findByGroupIdAndDay(g.getId(), start, end);
        } else if (beforeId != null && beforeId > 0) {
            msgs = messageRepository.findByGroupIdAndIdLessThanOrderByIdDesc(
                    g.getId(), beforeId, PageRequest.of(0, lim));
        } else {
            msgs = messageRepository.findByGroupIdOrderByIdDesc(g.getId(), PageRequest.of(0, lim));
        }

        // chronological for day query; reverse for cursor
        if (!StringUtils.hasText(onDay)) {
            msgs = new ArrayList<>(msgs);
            java.util.Collections.reverse(msgs);
        }
        return enrichMessages(msgs, userId);
    }

    @Transactional(readOnly = true)
    public List<String> listDays(Long userId, String publicId, int limit) {
        ChatGroup g = access.requireActiveGroup(publicId);
        access.requireUserMember(g, userId);
        int lim = Math.min(366, Math.max(1, limit <= 0 ? 90 : limit));
        return messageRepository.findMessageDays(g.getId(), lim).stream()
                .map(d -> d.toLocalDate().toString())
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listMedia(Long userId, String publicId, String kind, int page, int size) {
        ChatGroup g = access.requireActiveGroup(publicId);
        access.requireUserMember(g, userId);
        GroupAttachment.Kind k;
        try {
            k = GroupAttachment.Kind.valueOf(kind.trim().toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效 media kind");
        }
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size <= 0 ? 20 : size));
        Page<GroupAttachment> result = attachmentRepository.findByGroupIdAndKindOrderByIdDesc(
                g.getId(), k, PageRequest.of(p, s));
        List<Map<String, Object>> items = result.getContent().stream()
                .map(this::attachmentView)
                .toList();
        return Map.of(
                "items", items,
                "page", p,
                "size", s,
                "total", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        );
    }

    @Transactional
    public Map<String, Object> markReadUpTo(Long userId, String publicId, Long upToMessageId) {
        ChatGroup g = access.requireActiveGroup(publicId);
        GroupMember me = access.requireUserMember(g, userId);
        if (upToMessageId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 upToMessageId");
        }
        List<GroupMessage> recent = messageRepository.findByGroupIdAndIdLessThanOrderByIdDesc(
                g.getId(), upToMessageId + 1, PageRequest.of(0, 100));
        int n = 0;
        for (GroupMessage msg : recent) {
            if (msg.getId() > upToMessageId) continue;
            if (readRepository.findByMessageIdAndMemberId(msg.getId(), me.getId()).isEmpty()) {
                markRead(msg, me, 0L);
                n++;
            }
        }
        // 人类客户端已读：批量推送，前端按 upToMessageId 刷新头像
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memberId", me.getId());
        payload.put("upToMessageId", upToMessageId);
        payload.put("count", n);
        payload.put("reader", readBrief(me, 0L));
        eventPublisher.publish(g.getPublicId(), "READ", payload);
        return payload;
    }

    /** Agent / system 写消息入口 */
    @Transactional
    public Map<String, Object> postAgentMessage(ChatGroup g, GroupMember agentMember, String content, Long replyToId) {
        return postAgentMessage(g, agentMember, content, replyToId, true, null);
    }

    @Transactional
    public Map<String, Object> postAgentMessageWithThinking(
            ChatGroup g, GroupMember agentMember, String content, Long replyToId, String thinkingText
    ) {
        return postAgentMessage(g, agentMember, content, replyToId, true, thinkingText, null);
    }

    @Transactional
    public Map<String, Object> postAgentMessageWithTraces(
            ChatGroup g,
            GroupMember agentMember,
            String content,
            Long replyToId,
            String thinkingText,
            String executionText
    ) {
        return postAgentMessage(g, agentMember, content, replyToId, true, thinkingText, executionText);
    }

    /**
     * @param triggerDispatch false 时不触发其它 Agent（流式占位/增量更新用，避免递归派发）
     */
    @Transactional
    public Map<String, Object> postAgentMessage(
            ChatGroup g, GroupMember agentMember, String content, Long replyToId, boolean triggerDispatch
    ) {
        return postAgentMessage(g, agentMember, content, replyToId, triggerDispatch, null, null);
    }

    @Transactional
    public Map<String, Object> postAgentMessage(
            ChatGroup g,
            GroupMember agentMember,
            String content,
            Long replyToId,
            boolean triggerDispatch,
            String thinkingText
    ) {
        return postAgentMessage(g, agentMember, content, replyToId, triggerDispatch, thinkingText, null);
    }

    @Transactional
    public Map<String, Object> postAgentMessage(
            ChatGroup g,
            GroupMember agentMember,
            String content,
            Long replyToId,
            boolean triggerDispatch,
            String thinkingText,
            String executionText
    ) {
        GroupMessage msg = new GroupMessage();
        msg.setGroupId(g.getId());
        msg.setSenderType(GroupMessage.SenderType.AGENT);
        msg.setSenderAgentId(agentMember.getUserAgentId());
        msg.setContent(content);
        if (StringUtils.hasText(thinkingText)) {
            msg.setThinkingText(thinkingText.trim());
        }
        if (StringUtils.hasText(executionText)) {
            msg.setExecutionText(executionText.trim());
        }
        msg.setReplyToId(replyToId);
        messageRepository.saveAndFlush(msg);
        extractLinks(g.getId(), msg.getId(), content);
        // 自己发的消息不当作「已读回执」展示消耗；points=0
        markRead(msg, agentMember, 0L);
        // 并发 SMART 回复时禁止对同一托管实体 save（否则 OptimisticLock 吞掉后发言）
        groupRepository.touchActivity(g.getId());
        Map<String, Object> view = messageView(msg, List.of(), List.of(readBrief(agentMember, 0L)), null);
        eventPublisher.publish(g.getPublicId(), "MESSAGE", view);

        if (triggerDispatch) {
            // Agent 消息同样触发其他 SMART/MENTION Agent 识别（含 Agent 互 @）
            final ChatGroup groupRef = g;
            final GroupMessage msgRef = msg;
            final GroupMember senderRef = agentMember;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        agentDispatcher.dispatchAfterMessage(groupRef, msgRef, senderRef);
                    }
                });
            } else {
                agentDispatcher.dispatchAfterMessage(groupRef, msgRef, senderRef);
            }
        }
        return view;
    }

    /**
     * 原地更新 Agent 消息正文并推 SSE MESSAGE（前端按 id upsert）。
     * 不触发 Agent 派发。
     */
    @Transactional
    public Map<String, Object> updateAgentMessageContent(
            ChatGroup g, GroupMember agentMember, Long messageId, String content
    ) {
        if (messageId == null || !StringUtils.hasText(content)) {
            throw new IllegalArgumentException("messageId/content 无效");
        }
        GroupMessage msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("消息不存在"));
        if (!msg.getGroupId().equals(g.getId())) {
            throw new IllegalArgumentException("消息不属于该群");
        }
        if (msg.getSenderType() != GroupMessage.SenderType.AGENT
                || !Objects.equals(msg.getSenderAgentId(), agentMember.getUserAgentId())) {
            throw new IllegalArgumentException("只能更新本 Agent 自己的消息");
        }
        msg.setContent(content);
        messageRepository.saveAndFlush(msg);
        groupRepository.touchActivity(g.getId());
        Map<String, Object> view = messageView(msg, List.of(), List.of(readBrief(agentMember, 0L)), null);
        eventPublisher.publish(g.getPublicId(), "MESSAGE", view);
        return view;
    }

    @Transactional
    public void markMemberRead(GroupMessage msg, GroupMember member) {
        markMemberRead(msg, member, 0L);
    }

    /**
     * 标记成员已读。
     * 人类：客户端展示后调用，pointsSpent=0。
     * Agent：完成大模型分析后调用，pointsSpent 为本次分析消耗积分。
     */
    @Transactional
    public void markMemberRead(GroupMessage msg, GroupMember member, long pointsSpent) {
        GroupMessageRead row = markRead(msg, member, pointsSpent);
        ChatGroup g = groupRepository.findById(msg.getGroupId()).orElse(null);
        if (g != null) {
            long spent = row != null && row.getPointsSpent() != null ? row.getPointsSpent() : Math.max(0L, pointsSpent);
            eventPublisher.publish(g.getPublicId(), "READ", Map.of(
                    "messageId", msg.getId(),
                    "memberId", member.getId(),
                    "reader", readBrief(member, spent)
            ));
        }
    }

    @Transactional
    public void publishAgentStatus(ChatGroup g, GroupMember agentMember, String status) {
        eventPublisher.publish(g.getPublicId(), "AGENT_STATUS", Map.of(
                "memberId", agentMember.getId(),
                "userAgentId", agentMember.getUserAgentId(),
                "status", status
        ));
    }

    /** 本机/Agent 思考过程实时推送（不落库气泡；前端显示在回复气泡上方） */
    public void publishAgentThinking(ChatGroup g, Map<String, Object> payload) {
        if (g == null || payload == null || payload.isEmpty()) return;
        eventPublisher.publish(g.getPublicId(), "AGENT_THINKING", payload);
    }

    /** 给已有 Agent 消息补上思考过程正文 */
    @Transactional
    public void attachThinking(Long messageId, String thinkingText) {
        if (messageId == null || !StringUtils.hasText(thinkingText)) return;
        messageRepository.findById(messageId).ifPresent(msg -> {
            msg.setThinkingText(thinkingText.trim());
            messageRepository.save(msg);
        });
    }

    private GroupMessageRead markRead(GroupMessage msg, GroupMember member, long pointsSpent) {
        long add = Math.max(0L, pointsSpent);
        var existing = readRepository.findByMessageIdAndMemberId(msg.getId(), member.getId());
        if (existing.isPresent()) {
            GroupMessageRead r = existing.get();
            if (add > 0) {
                long cur = r.getPointsSpent() != null ? r.getPointsSpent() : 0L;
                r.setPointsSpent(cur + add);
                return readRepository.save(r);
            }
            return r;
        }
        GroupMessageRead r = new GroupMessageRead();
        r.setMessageId(msg.getId());
        r.setGroupId(msg.getGroupId());
        r.setMemberId(member.getId());
        r.setReadAt(LocalDateTime.now());
        r.setPointsSpent(add);
        return readRepository.save(r);
    }

    private List<GroupAttachment> persistAttachments(Long groupId, Long messageId, List<AttachmentIn> atts, String content) {
        List<GroupAttachment> out = new ArrayList<>();
        for (AttachmentIn a : atts) {
            if (a == null || !StringUtils.hasText(a.url())) continue;
            GroupAttachment.Kind kind = parseKind(a.kind());
            String url = a.url().trim();
            // data URL 远超 1024 列宽；必须落 COS，禁止截断后入库（否则气泡里图片不可见）
            if ((kind == GroupAttachment.Kind.IMAGE || kind == GroupAttachment.Kind.VIDEO || kind == GroupAttachment.Kind.AUDIO)
                    && url.startsWith("data:")) {
                if (!chatCos.isUsable()) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "图片存储未配置，暂无法发送图片");
                }
                try {
                    List<String> persisted = chatCos.persistUserImages(List.of(url));
                    if (persisted.isEmpty() || !StringUtils.hasText(persisted.get(0))) {
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图片上传失败");
                    }
                    url = persisted.get(0).trim();
                } catch (ResponseStatusException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图片上传失败: " + e.getMessage());
                }
            }
            if (url.length() > 1024) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "附件地址过长");
            }
            GroupAttachment row = new GroupAttachment();
            row.setGroupId(groupId);
            row.setMessageId(messageId);
            row.setKind(kind);
            row.setUrl(url);
            row.setMime(trim(a.mime(), 128));
            row.setSizeBytes(a.sizeBytes());
            row.setTitle(trim(a.title(), 256));
            attachmentRepository.save(row);
            out.add(row);
        }
        return out;
    }

    private void extractLinks(Long groupId, Long messageId, String content) {
        if (!StringUtils.hasText(content)) return;
        Matcher m = URL_PATTERN.matcher(content);
        while (m.find()) {
            String url = m.group();
            GroupAttachment row = new GroupAttachment();
            row.setGroupId(groupId);
            row.setMessageId(messageId);
            row.setKind(GroupAttachment.Kind.LINK);
            row.setUrl(trim(url, 1024));
            row.setTitle(trim(url, 256));
            attachmentRepository.save(row);
        }
    }

    private GroupAttachment.Kind parseKind(String raw) {
        if (!StringUtils.hasText(raw)) return GroupAttachment.Kind.FILE;
        try {
            return GroupAttachment.Kind.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return GroupAttachment.Kind.FILE;
        }
    }

    private List<Map<String, Object>> enrichMessages(List<GroupMessage> msgs, Long viewerUserId) {
        if (msgs.isEmpty()) return List.of();
        List<Long> ids = msgs.stream().map(GroupMessage::getId).toList();
        Map<Long, List<GroupAttachment>> atts = attachmentRepository.findByMessageIdIn(ids).stream()
                .collect(Collectors.groupingBy(GroupAttachment::getMessageId));
        Map<Long, List<GroupMessageRead>> reads = readRepository.findByMessageIdIn(ids).stream()
                .collect(Collectors.groupingBy(GroupMessageRead::getMessageId));
        Map<Long, GroupMember> membersById = memberRepository.findByGroupIdOrderByIdAsc(
                msgs.get(0).getGroupId()).stream()
                .collect(Collectors.toMap(GroupMember::getId, x -> x, (a, b) -> a));

        List<Long> replyIds = msgs.stream()
                .map(GroupMessage::getReplyToId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, GroupMessage> parents = replyIds.isEmpty()
                ? Map.of()
                : messageRepository.findAllById(replyIds).stream()
                        .collect(Collectors.toMap(GroupMessage::getId, x -> x, (a, b) -> a));
        List<Long> missingParentAttIds = parents.keySet().stream()
                .filter(id -> !atts.containsKey(id))
                .toList();
        if (!missingParentAttIds.isEmpty()) {
            attachmentRepository.findByMessageIdIn(missingParentAttIds).forEach(a ->
                    atts.computeIfAbsent(a.getMessageId(), k -> new ArrayList<>()).add(a));
        }

        List<Long> approvalCardIds = msgs.stream()
                .filter(m -> m.getMessageKind() == GroupMessage.MessageKind.MENTION_APPROVAL)
                .map(GroupMessage::getId)
                .toList();
        Map<Long, GroupMentionApproval> approvals = mentionApprovalService.byCardMessageIds(approvalCardIds);

        List<Map<String, Object>> out = new ArrayList<>();
        for (GroupMessage msg : msgs) {
            List<Map<String, Object>> readers = reads.getOrDefault(msg.getId(), List.of()).stream()
                    .map(r -> {
                        GroupMember gm = membersById.get(r.getMemberId());
                        long spent = r.getPointsSpent() != null ? r.getPointsSpent() : 0L;
                        if (gm == null) {
                            Map<String, Object> bare = new LinkedHashMap<>();
                            bare.put("memberId", r.getMemberId());
                            bare.put("pointsSpent", spent);
                            return bare;
                        }
                        return readBrief(gm, spent);
                    })
                    .toList();
            out.add(messageView(msg, atts.getOrDefault(msg.getId(), List.of()), readers, parents, atts,
                    viewerUserId, approvals));
        }
        return out;
    }

    private Map<String, Object> messageView(
            GroupMessage msg,
            List<GroupAttachment> atts,
            List<Map<String, Object>> readers,
            Long viewerUserId
    ) {
        Map<Long, GroupMessage> parents = Map.of();
        Map<Long, List<GroupAttachment>> parentAtts = Map.of();
        if (msg.getReplyToId() != null) {
            GroupMessage parent = messageRepository.findById(msg.getReplyToId()).orElse(null);
            if (parent != null) {
                parents = Map.of(parent.getId(), parent);
                parentAtts = Map.of(parent.getId(), attachmentRepository.findByMessageId(parent.getId()));
            }
        }
        Map<Long, GroupMentionApproval> approvals = msg.getMessageKind() == GroupMessage.MessageKind.MENTION_APPROVAL
                ? mentionApprovalService.byCardMessageIds(List.of(msg.getId()))
                : Map.of();
        return messageView(msg, atts, readers, parents, parentAtts, viewerUserId, approvals);
    }

    private Map<String, Object> messageView(
            GroupMessage msg,
            List<GroupAttachment> atts,
            List<Map<String, Object>> readers,
            Map<Long, GroupMessage> parents,
            Map<Long, List<GroupAttachment>> allAtts,
            Long viewerUserId,
            Map<Long, GroupMentionApproval> approvalsByCardId
    ) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", msg.getId());
        v.put("groupId", msg.getGroupId());
        v.put("senderType", msg.getSenderType().name());
        v.put("senderUserId", msg.getSenderUserId());
        v.put("senderAgentId", msg.getSenderAgentId());
        v.put("content", msg.getContent());
        if (StringUtils.hasText(msg.getThinkingText())) {
            v.put("thinking", msg.getThinkingText());
        }
        if (StringUtils.hasText(msg.getExecutionText())) {
            v.put("execution", msg.getExecutionText());
        }
        v.put("replyToId", msg.getReplyToId());
        v.put("clientMsgId", msg.getClientMsgId());
        GroupMessage.MessageKind kind = msg.getMessageKind() != null
                ? msg.getMessageKind() : GroupMessage.MessageKind.CHAT;
        v.put("messageKind", kind.name());
        v.put("createTime", msg.getCreateTime());
        fillSender(v, msg);
        v.put("attachments", atts.stream().map(this::attachmentView).toList());
        v.put("reads", readers);
        if (msg.getReplyToId() != null) {
            GroupMessage parent = parents.get(msg.getReplyToId());
            if (parent == null) {
                Map<String, Object> gone = new LinkedHashMap<>();
                gone.put("id", msg.getReplyToId());
                gone.put("deleted", true);
                gone.put("contentPreview", "原消息已删除");
                v.put("replyTo", gone);
            } else {
                v.put("replyTo", replySnapshot(parent, allAtts.getOrDefault(parent.getId(), List.of())));
            }
        }
        if (kind == GroupMessage.MessageKind.MENTION_APPROVAL && approvalsByCardId != null) {
            GroupMentionApproval row = approvalsByCardId.get(msg.getId());
            if (row != null) {
                v.put("mentionApproval", mentionApprovalService.approvalView(row, viewerUserId));
            }
        }
        return v;
    }

    private void fillSender(Map<String, Object> v, GroupMessage msg) {
        if (msg.getSenderType() == GroupMessage.SenderType.USER && msg.getSenderUserId() != null) {
            userRepository.findById(msg.getSenderUserId()).ifPresent(u -> {
                v.put("senderName", StringUtils.hasText(u.getDisplayName()) ? u.getDisplayName() : u.getUsername());
                v.put("senderAvatarUrl", u.getAvatarUrl());
            });
        } else if (msg.getSenderType() == GroupMessage.SenderType.AGENT && msg.getSenderAgentId() != null) {
            userAgentRepository.findById(msg.getSenderAgentId()).ifPresent(ua -> {
                v.put("senderName", ua.getName());
                v.put("senderOriginalName", identityService.originalName(ua));
                v.put("senderCareerName", identityService.careerName(ua));
                v.put("senderDisplayTitle", identityService.displayTitle(ua));
                v.put("senderAvatarUrl", ua.getAvatarUrl());
            });
        } else {
            v.put("senderName", "系统");
        }
    }

    private Map<String, Object> replySnapshot(GroupMessage parent, List<GroupAttachment> parentAtts) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", parent.getId());
        r.put("deleted", false);
        r.put("senderType", parent.getSenderType().name());
        fillSender(r, parent);
        String preview = parent.getContent() != null ? parent.getContent().trim() : "";
        if (preview.length() > 160) preview = preview.substring(0, 160) + "…";
        String imageUrl = parentAtts.stream()
                .filter(a -> a.getKind() == GroupAttachment.Kind.IMAGE)
                .map(GroupAttachment::getUrl)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        if (!StringUtils.hasText(preview) && imageUrl != null) {
            preview = "[图片]";
        }
        r.put("contentPreview", preview);
        r.put("imageUrl", imageUrl);
        return r;
    }

    private Map<String, Object> attachmentView(GroupAttachment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("messageId", a.getMessageId());
        m.put("kind", a.getKind().name());
        m.put("url", a.getUrl());
        m.put("mime", a.getMime());
        m.put("sizeBytes", a.getSizeBytes());
        m.put("title", a.getTitle());
        m.put("createTime", a.getCreateTime());
        return m;
    }

    private Map<String, Object> readBrief(GroupMember m, long pointsSpent) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("memberId", m.getId());
        v.put("memberType", m.getMemberType().name());
        v.put("pointsSpent", Math.max(0L, pointsSpent));
        if (m.getMemberType() == GroupMember.MemberType.USER) {
            v.put("userId", m.getUserId());
            userRepository.findById(m.getUserId()).ifPresent(u -> {
                v.put("name", StringUtils.hasText(u.getDisplayName()) ? u.getDisplayName() : u.getUsername());
                v.put("avatarUrl", u.getAvatarUrl());
            });
        } else {
            v.put("userAgentId", m.getUserAgentId());
            userAgentRepository.findById(m.getUserAgentId()).ifPresent(ua -> {
                v.put("name", ua.getName());
                v.put("avatarUrl", ua.getAvatarUrl());
            });
        }
        return v;
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    public record SendRequest(
            String content,
            Long replyToId,
            String clientMsgId,
            List<AttachmentIn> attachments
    ) {}

    public record AttachmentIn(
            String kind,
            String url,
            String mime,
            Long sizeBytes,
            String title
    ) {}
}
