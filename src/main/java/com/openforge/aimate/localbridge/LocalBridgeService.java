package com.openforge.aimate.localbridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.agent.AgentPromptService;
import com.openforge.aimate.config.LocalBridgeProperties;
import com.openforge.aimate.agent.SessionMessageService;
import com.openforge.aimate.domain.AgentExecutionMode;
import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.domain.ChatGroup;
import com.openforge.aimate.domain.GroupMember;
import com.openforge.aimate.domain.GroupMessage;
import com.openforge.aimate.domain.LocalBridgeDevice;
import com.openforge.aimate.domain.LocalBridgeTask;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.group.GroupMessageService;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.group.GroupConversationViewService;
import com.openforge.aimate.memory.GroupMemoryCaptureService;
import com.openforge.aimate.memory.SoulMemoryBridge;
import com.openforge.aimate.domain.SessionMessage;
import com.openforge.aimate.repository.AgentSessionRepository;
import com.openforge.aimate.repository.ChatGroupRepository;
import com.openforge.aimate.repository.GroupMemberRepository;
import com.openforge.aimate.repository.GroupMessageRepository;
import com.openforge.aimate.repository.LocalBridgeDeviceRepository;
import com.openforge.aimate.repository.LocalBridgeTaskRepository;
import com.openforge.aimate.repository.SessionMessageRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class LocalBridgeService {

    private static final int CONTEXT_N = 40;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final int ONLINE_TTL_SECONDS = 45;

    private final UserAgentRepository userAgentRepository;
    private final LocalBridgeTaskRepository taskRepository;
    private final LocalBridgeDeviceRepository deviceRepository;
    private final ChatGroupRepository groupRepository;
    private final GroupMessageRepository messageRepository;
    private final GroupMemberRepository memberRepository;
    private final AgentPromptService agentPromptService;
    private final LocalBridgeProperties properties;
    private final ObjectMapper objectMapper;
    private final SoulMemoryBridge soulMemoryBridge;
    private final GroupMemoryCaptureService groupMemoryCaptureService;
    private final GroupConversationViewService conversationViewService;
    private final GroupMessageService messageService;
    private final AgentSessionRepository sessionRepository;
    private final SessionMessageService sessionMessageService;
    private final SessionMessageRepository sessionMessageRepository;
    private final TransactionTemplate transactionTemplate;

    public LocalBridgeService(
            UserAgentRepository userAgentRepository,
            LocalBridgeTaskRepository taskRepository,
            LocalBridgeDeviceRepository deviceRepository,
            ChatGroupRepository groupRepository,
            GroupMessageRepository messageRepository,
            GroupMemberRepository memberRepository,
            AgentPromptService agentPromptService,
            LocalBridgeProperties properties,
            ObjectMapper objectMapper,
            SoulMemoryBridge soulMemoryBridge,
            GroupMemoryCaptureService groupMemoryCaptureService,
            GroupConversationViewService conversationViewService,
            @Lazy GroupMessageService messageService,
            AgentSessionRepository sessionRepository,
            @Lazy SessionMessageService sessionMessageService,
            SessionMessageRepository sessionMessageRepository,
            TransactionTemplate transactionTemplate
    ) {
        this.userAgentRepository = userAgentRepository;
        this.taskRepository = taskRepository;
        this.deviceRepository = deviceRepository;
        this.groupRepository = groupRepository;
        this.messageRepository = messageRepository;
        this.memberRepository = memberRepository;
        this.agentPromptService = agentPromptService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.soulMemoryBridge = soulMemoryBridge;
        this.groupMemoryCaptureService = groupMemoryCaptureService;
        this.conversationViewService = conversationViewService;
        this.messageService = messageService;
        this.sessionRepository = sessionRepository;
        this.sessionMessageService = sessionMessageService;
        this.sessionMessageRepository = sessionMessageRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public static boolean isLocalBridge(UserAgent ua) {
        return ua != null && ua.getExecutionMode() == AgentExecutionMode.LOCAL_BRIDGE;
    }

    /** null 视为开启（兼容旧数据） */
    public static boolean isDmLocalAssistEnabled(UserAgent ua) {
        return isLocalBridge(ua) && (ua.getLocalAssistDm() == null || Boolean.TRUE.equals(ua.getLocalAssistDm()));
    }

    public static boolean isGroupLocalAssistEnabled(UserAgent ua) {
        return isLocalBridge(ua) && (ua.getLocalAssistGroup() == null || Boolean.TRUE.equals(ua.getLocalAssistGroup()));
    }

    @Transactional(readOnly = true)
    public boolean isLocalBridgeAgent(Long userAgentId) {
        return userAgentRepository.findById(userAgentId).map(LocalBridgeService::isLocalBridge).orElse(false);
    }

    /**
     * 本机协助说明文案：客户端在线 →「转发到 Connector」；仅提示词 →「派发任务」；勿混用。
     */
    @Transactional(readOnly = true)
    public String describeDispatchHint(UserAgent ua) {
        if (ua == null) return "已交由本机协助";
        LocalDateTime now = LocalDateTime.now();
        boolean clientOnline = false;
        boolean promptPending = false;
        for (LocalBridgeDevice d : deviceRepository.findByUserAgentIdOrderByLastSeenAtDesc(ua.getId())) {
            if (d.isUserDisconnected()) continue;
            boolean online = d.getLastSeenAt() != null
                    && java.time.temporal.ChronoUnit.SECONDS.between(d.getLastSeenAt(), now) <= ONLINE_TTL_SECONDS;
            if (d.getLinkMode() == LocalBridgeDevice.LinkMode.CLIENT && online) clientOnline = true;
            if (d.getLinkMode() == LocalBridgeDevice.LinkMode.PROMPT) promptPending = true;
        }
        if (clientOnline) return "已转发到本机 Connector，等待本地应用跑完后回传结果";
        if (promptPending) return "已派发任务，等待提示词设备领取";
        return "本机协助已排队（客户端未在线，上线后由 Connector 领取）";
    }

    /**
     * 用户侧：签发/轮换接入令牌，并返回可复制提示词（明文 token 仅此一次完整返回）。
     */
    @Transactional
    public Map<String, Object> issueConnectBundle(Long ownerUserId, Long userAgentId, boolean rotate) {
        UserAgent ua = userAgentRepository.findByIdAndUserId(userAgentId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 不存在"));
        if (!isLocalBridge(ua)) {
            throw new IllegalStateException("该 Agent 不是本地 AI 职业，无需接入");
        }
        if (ua.getStatus() != UserAgent.Status.ACTIVE) {
            throw new IllegalStateException("Agent 不可用");
        }

        String plainToken = null;
        if (rotate || !StringUtils.hasText(ua.getLocalBridgeTokenHash())) {
            plainToken = newToken();
            ua.setLocalBridgeTokenHash(LocalBridgeAuthFilter.sha256Hex(plainToken));
            ua.setLocalBridgeTokenPrefix(plainToken.substring(0, Math.min(12, plainToken.length())));
            userAgentRepository.save(ua);
        }

        String apiBase = trimSlash(properties.getPublicApiBase());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userAgentId", ua.getId());
        out.put("agentName", ua.getName());
        out.put("executionMode", ua.getExecutionMode().name());
        out.put("apiBase", apiBase);
        out.put("tokenPrefix", ua.getLocalBridgeTokenPrefix());
        out.put("tokenRotated", plainToken != null);
        out.put("hasToken", StringUtils.hasText(ua.getLocalBridgeTokenHash()));
        out.put("pollIntervalSeconds", properties.getPollIntervalSeconds());
        if (plainToken != null) {
            out.put("token", plainToken);
            out.put("prompt", buildPrompt(ua, apiBase, plainToken, null));
        } else {
            out.put("prompt", buildPromptPlaceholder(ua, apiBase));
            out.put("hint", "令牌已存在。若本地未保存，请点「重新生成令牌」获取新提示词。");
        }
        return out;
    }

    @Transactional
    public LocalBridgeTask enqueueFromMention(
            ChatGroup group,
            GroupMessage userMsg,
            GroupMember agentMember,
            UserAgent ua
    ) {
        return enqueueFromMention(group, userMsg, agentMember, ua, null);
    }

    /**
     * 群聊派发：在云端正常回复之后，由 Agent 判断需要控本机时调用。
     * {@code instructionOverride} 非空时用其作为本机任务说明，否则用触发消息正文。
     * 幂等键 g:{triggerMessageId}：同消息不重复入队（含已完成历史）。
     */
    @Transactional
    public LocalBridgeTask enqueueFromMention(
            ChatGroup group,
            GroupMessage userMsg,
            GroupMember agentMember,
            UserAgent ua,
            String instructionOverride
    ) {
        if (!isGroupLocalAssistEnabled(ua)) return null;

        String instruction = StringUtils.hasText(instructionOverride)
                ? instructionOverride.trim()
                : (userMsg != null ? userMsg.getContent() : null);
        if (!StringUtils.hasText(instruction)) return null;

        String idemKey = userMsg != null && userMsg.getId() != null
                ? "g:" + userMsg.getId()
                : "g:anon:" + shortHash(group.getId() + "|" + instruction);
        LocalBridgeTask existing = findByIdempotency(ua.getId(), idemKey);
        if (existing != null) {
            // 任意终态或进行中：同触发消息不二次派发
            try {
                messageService.publishAgentStatus(group, agentMember,
                        existing.getStatus() == LocalBridgeTask.Status.PENDING
                                || existing.getStatus() == LocalBridgeTask.Status.CLAIMED
                                ? "waiting_local" : "idle");
            } catch (Exception ignored) {}
            return existing;
        }

        // 同群现行意图提升：PENDING + 进行中的 CLAIMED 一律 superseded（权威在服务端）
        long epoch = resolveIntentEpoch(userMsg != null ? userMsg.getId() : null);
        supersedeActiveInGroup(ua.getId(), group.getId(), null, "被更新的本机意图取代 (epoch=" + epoch + ")");

        Map<String, Object> ctx = buildContextSnapshot(group, userMsg, ua);
        LocalBridgeTask task = newPendingTask(ua.getId(), instruction, idemKey);
        task.setGroupId(group.getId());
        task.setSessionId(null);
        task.setTriggerMessageId(userMsg != null ? userMsg.getId() : null);
        task.setIntentEpoch(epoch);
        task.setSuperseded(Boolean.FALSE);
        try {
            task.setContextJson(objectMapper.writeValueAsString(ctx));
        } catch (Exception e) {
            task.setContextJson("{}");
        }
        task = saveIdempotent(task, ua.getId(), idemKey);
        try {
            messageService.publishAgentStatus(group, agentMember, "waiting_local");
        } catch (Exception e) {
            log.debug("[LocalBridge] status publish failed: {}", e.getMessage());
        }
        return task;
    }

    private void cancelPendingInGroup(Long userAgentId, Long groupId, Long keepId, String reason) {
        if (userAgentId == null || groupId == null) return;
        try {
            int n = taskRepository.cancelPendingInGroup(
                    userAgentId,
                    groupId,
                    keepId,
                    LocalBridgeTask.Status.PENDING,
                    LocalBridgeTask.Status.CANCELLED,
                    LocalDateTime.now(),
                    trim(reason, 256));
            if (n > 0) {
                log.info("[LocalBridge] cancelled {} stale PENDING task(s) agent={} group={}",
                        n, userAgentId, groupId);
            }
        } catch (Exception e) {
            log.warn("[LocalBridge] cancelPendingInGroup failed: {}", e.getMessage());
        }
    }

    private void cancelPendingInSession(Long userAgentId, String sessionId, Long keepId, String reason) {
        if (userAgentId == null || !StringUtils.hasText(sessionId)) return;
        try {
            int n = taskRepository.cancelPendingInSession(
                    userAgentId,
                    sessionId,
                    keepId,
                    LocalBridgeTask.Status.PENDING,
                    LocalBridgeTask.Status.CANCELLED,
                    LocalDateTime.now(),
                    trim(reason, 256));
            if (n > 0) {
                log.info("[LocalBridge] cancelled {} stale PENDING task(s) agent={} session={}",
                        n, userAgentId, sessionId);
            }
        } catch (Exception e) {
            log.warn("[LocalBridge] cancelPendingInSession failed: {}", e.getMessage());
        }
    }

    /** 群聊：取消同 scope 全部 PENDING/CLAIMED 并标 superseded */
    private void supersedeActiveInGroup(Long userAgentId, Long groupId, Long keepId, String reason) {
        if (userAgentId == null || groupId == null) return;
        try {
            int n = taskRepository.supersedeActiveInGroup(
                    userAgentId,
                    groupId,
                    keepId,
                    LocalBridgeTask.Status.PENDING,
                    LocalBridgeTask.Status.CLAIMED,
                    LocalBridgeTask.Status.CANCELLED,
                    LocalDateTime.now(),
                    trim(reason, 256));
            if (n > 0) {
                log.info("[LocalBridge] superseded {} active task(s) agent={} group={}",
                        n, userAgentId, groupId);
            }
        } catch (Exception e) {
            log.warn("[LocalBridge] supersedeActiveInGroup failed: {}", e.getMessage());
            cancelPendingInGroup(userAgentId, groupId, keepId, reason);
        }
    }

    private void supersedeActiveInSession(Long userAgentId, String sessionId, Long keepId, String reason) {
        if (userAgentId == null || !StringUtils.hasText(sessionId)) return;
        try {
            int n = taskRepository.supersedeActiveInSession(
                    userAgentId,
                    sessionId,
                    keepId,
                    LocalBridgeTask.Status.PENDING,
                    LocalBridgeTask.Status.CLAIMED,
                    LocalBridgeTask.Status.CANCELLED,
                    LocalDateTime.now(),
                    trim(reason, 256));
            if (n > 0) {
                log.info("[LocalBridge] superseded {} active task(s) agent={} session={}",
                        n, userAgentId, sessionId);
            }
        } catch (Exception e) {
            log.warn("[LocalBridge] supersedeActiveInSession failed: {}", e.getMessage());
            cancelPendingInSession(userAgentId, sessionId, keepId, reason);
        }
    }

    private long resolveIntentEpoch(Long triggerMessageId) {
        if (triggerMessageId != null && triggerMessageId > 0) {
            return triggerMessageId;
        }
        return System.currentTimeMillis();
    }

    /** 当前任务是否已被同 scope 更新的意图取代（DB 标记或 epoch 落后） */
    private boolean isSupersededNow(LocalBridgeTask task) {
        if (task == null) return true;
        if (Boolean.TRUE.equals(task.getSuperseded())) return true;
        if (task.getStatus() == LocalBridgeTask.Status.CANCELLED
                && Boolean.TRUE.equals(task.getSuperseded())) {
            return true;
        }
        long epoch = task.getIntentEpoch() != null ? task.getIntentEpoch() : 0L;
        Long max = null;
        if (task.getGroupId() != null) {
            max = taskRepository.maxIntentEpochInGroup(task.getUserAgentId(), task.getGroupId());
        } else if (StringUtils.hasText(task.getSessionId())) {
            max = taskRepository.maxIntentEpochInSession(task.getUserAgentId(), task.getSessionId());
        }
        return max != null && max > epoch;
    }

    private void markTaskSuperseded(LocalBridgeTask task, String reason) {
        if (task == null) return;
        if (task.getStatus() == LocalBridgeTask.Status.DONE
                || task.getStatus() == LocalBridgeTask.Status.FAILED) {
            return;
        }
        task.setSuperseded(Boolean.TRUE);
        task.setStatus(LocalBridgeTask.Status.CANCELLED);
        task.setCompletedAt(LocalDateTime.now());
        task.setLeaseUntil(null);
        task.setErrorMessage(trim(reason, 1024));
        taskRepository.save(task);
        notifyStatus(task, "idle");
    }

    private Map<String, Object> supersededResponse(LocalBridgeTask task) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", false);
        out.put("superseded", true);
        out.put("status", task.getStatus() != null ? task.getStatus().name() : "CANCELLED");
        out.put("msgId", task.getTriggerMessageId());
        out.put("intentEpoch", task.getIntentEpoch());
        Long active = null;
        if (task.getGroupId() != null) {
            active = taskRepository.maxIntentEpochInGroup(task.getUserAgentId(), task.getGroupId());
        } else if (StringUtils.hasText(task.getSessionId())) {
            active = taskRepository.maxIntentEpochInSession(task.getUserAgentId(), task.getSessionId());
        }
        out.put("activeIntentEpoch", active);
        out.put("error", "任务已被更新的用户意图取代，请中止并丢弃结果");
        return out;
    }

    /**
     * 领取前：每个群/会话只保留最新一单 PENDING，其余作废。
     */
    private void supersedeOlderPending(Long userAgentId) {
        if (userAgentId == null) return;
        List<LocalBridgeTask> pending = taskRepository
                .findByUserAgentIdAndStatusOrderByIdDesc(userAgentId, LocalBridgeTask.Status.PENDING);
        if (pending.size() <= 1) return;
        java.util.HashSet<String> keepScope = new java.util.HashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (LocalBridgeTask t : pending) {
            String scope = t.getGroupId() != null
                    ? "g:" + t.getGroupId()
                    : (StringUtils.hasText(t.getSessionId()) ? "s:" + t.getSessionId() : "x:" + t.getId());
            if (keepScope.add(scope)) continue;
            t.setStatus(LocalBridgeTask.Status.CANCELLED);
            t.setCompletedAt(now);
            t.setErrorMessage("被更新的本机任务取代");
            taskRepository.save(t);
        }
    }

    /**
     * 单聊派发：云端对话中 Agent 调用 dispatch_local_task 时入队。
     * 幂等键优先用调用方传入；否则 d:{sessionId}:{lastUserMsgId}:{instructionHash}。
     * 同会话同时仅允许一个进行中任务。
     */
    @Transactional
    public Map<String, Object> enqueueFromSession(UserAgent ua, AgentSession session, String instruction) {
        return enqueueFromSession(ua, session, instruction, null);
    }

    @Transactional
    public Map<String, Object> enqueueFromSession(
            UserAgent ua, AgentSession session, String instruction, String idempotencyKey
    ) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!isDmLocalAssistEnabled(ua)) {
            out.put("ok", false);
            out.put("error", "该 Agent 已关闭「单聊使用本机设备」");
            return out;
        }
        if (session == null || !StringUtils.hasText(session.getSessionId())) {
            out.put("ok", false);
            out.put("error", "会话无效");
            return out;
        }
        if (!StringUtils.hasText(instruction)) {
            out.put("ok", false);
            out.put("error", "instruction 不能为空");
            return out;
        }
        String trimmed = instruction.trim();
        Long lastUserMsgId = latestUserMessageId(session);
        long epoch = resolveIntentEpoch(lastUserMsgId);
        // 新意图直接取代同会话 PENDING/CLAIMED（客户端续租会收到 superseded）
        supersedeActiveInSession(
                ua.getId(), session.getSessionId(), null, "被更新的本机意图取代 (epoch=" + epoch + ")");

        String idemKey = StringUtils.hasText(idempotencyKey)
                ? trim(idempotencyKey, 128)
                : buildDmIdempotencyKey(session, trimmed);
        LocalBridgeTask existing = findByIdempotency(ua.getId(), idemKey);
        if (existing != null) {
            if (existing.getStatus() == LocalBridgeTask.Status.PENDING
                    || existing.getStatus() == LocalBridgeTask.Status.CLAIMED) {
                out.put("ok", true);
                out.put("taskId", existing.getPublicId());
                out.put("status", existing.getStatus().name());
                out.put("idempotent", true);
                out.put("hint", describeDispatchHint(ua));
                return out;
            }
            // 终态后同 key 再派：换新 key，允许用户重试
            idemKey = trim(idemKey + ":r" + System.currentTimeMillis(), 128);
        }

        LocalBridgeTask task = newPendingTask(ua.getId(), trimmed, idemKey);
        task.setGroupId(null);
        task.setSessionId(session.getSessionId());
        task.setTriggerMessageId(lastUserMsgId);
        task.setIntentEpoch(epoch);
        task.setSuperseded(Boolean.FALSE);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("channel", "dm");
        ctx.put("sessionId", session.getSessionId());
        ctx.put("agentName", ua.getName());
        ctx.put("instruction", trimmed);
        ctx.put("idempotencyKey", idemKey);
        if (lastUserMsgId != null) ctx.put("triggerMessageId", lastUserMsgId);
        try {
            task.setContextJson(objectMapper.writeValueAsString(ctx));
        } catch (Exception e) {
            task.setContextJson("{}");
        }
        task = saveIdempotent(task, ua.getId(), idemKey);
        out.put("ok", true);
        out.put("taskId", task.getPublicId());
        out.put("status", task.getStatus().name());
        out.put("idempotencyKey", task.getIdempotencyKey());
        out.put("hint", describeDispatchHint(ua));
        return out;
    }

    /**
     * 领取下一任务。{@code waitSeconds>0} 时为 HTTP 长轮询。
     * 领取前先回收本 Agent 过期租约；用条件 UPDATE 防双领。
     */
    public Map<String, Object> claimNext(LocalBridgePrincipal principal, int waitSeconds) {
        return claimNext(principal, waitSeconds, null);
    }

    public Map<String, Object> claimNext(LocalBridgePrincipal principal, int waitSeconds, String deviceKey) {
        int wait = Math.min(Math.max(waitSeconds, 0), 30);
        long deadline = System.currentTimeMillis() + wait * 1000L;
        String device = StringUtils.hasText(deviceKey) ? deviceKey.trim() : null;
        while (true) {
            Map<String, Object> out = transactionTemplate.execute(status -> tryClaimOnce(principal, device));
            if (out == null) {
                out = Map.of("ok", true, "task", null);
            }
            if (out.get("task") != null || wait <= 0) {
                return out;
            }
            if (System.currentTimeMillis() >= deadline) {
                return out;
            }
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return out;
            }
        }
    }

    /** 兼容旧调用：立即返回（无等待）。 */
    public Map<String, Object> claimNext(LocalBridgePrincipal principal) {
        return claimNext(principal, 0, null);
    }

    private Map<String, Object> tryClaimOnce(LocalBridgePrincipal principal, String deviceKey) {
        UserAgent ua = requireActiveBridge(principal);
        LocalDateTime now = LocalDateTime.now();
        taskRepository.reclaimExpiredLeasesForAgent(
                ua.getId(), now, LocalBridgeTask.Status.PENDING, LocalBridgeTask.Status.CLAIMED);
        supersedeOlderPending(ua.getId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        // 最多尝试几单，避免竞态空转；优先最新一单
        for (int attempt = 0; attempt < 5; attempt++) {
            LocalBridgeTask candidate = taskRepository
                    .findFirstByUserAgentIdAndStatusOrderByIdDesc(ua.getId(), LocalBridgeTask.Status.PENDING)
                    .orElse(null);
            if (candidate == null) {
                out.put("task", null);
                return out;
            }
            // 租约回收可能复活旧单：若同群/会话已有更新触发消息 / 更高 epoch，直接作废
            if (cancelIfStaleTrigger(candidate, now) || isSupersededNow(candidate)) {
                if (candidate.getStatus() == LocalBridgeTask.Status.PENDING) {
                    markTaskSuperseded(candidate, "领取前发现意图已过期");
                }
                continue;
            }
            int leaseSec = Math.max(60, properties.getClaimLeaseSeconds());
            LocalDateTime leaseUntil = now.plusSeconds(leaseSec);
            int updated = taskRepository.claimIfPending(
                    candidate.getId(),
                    LocalBridgeTask.Status.CLAIMED,
                    LocalBridgeTask.Status.PENDING,
                    now,
                    leaseUntil,
                    deviceKey);
            if (updated == 1) {
                LocalBridgeTask task = taskRepository.findById(candidate.getId()).orElse(candidate);
                notifyStatus(task, "working");
                out.put("task", taskView(task, true));
                out.put("leaseUntil", leaseUntil.toString());
                out.put("leaseSeconds", leaseSec);
                return out;
            }
            // 被别人抢走，换下一单
        }
        out.put("task", null);
        return out;
    }

    /**
     * 若同 scope 已存在更大 triggerMessageId 的任务（非 CANCELLED），则本单过期作废。
     * @return true 表示已作废，调用方应换下一单
     */
    private boolean cancelIfStaleTrigger(LocalBridgeTask candidate, LocalDateTime now) {
        if (candidate.getTriggerMessageId() == null) return false;
        long newer;
        if (candidate.getGroupId() != null) {
            newer = taskRepository.countByUserAgentIdAndGroupIdAndTriggerMessageIdGreaterThanAndStatusNot(
                    candidate.getUserAgentId(),
                    candidate.getGroupId(),
                    candidate.getTriggerMessageId(),
                    LocalBridgeTask.Status.CANCELLED);
        } else if (StringUtils.hasText(candidate.getSessionId())) {
            newer = taskRepository.countByUserAgentIdAndSessionIdAndTriggerMessageIdGreaterThanAndStatusNot(
                    candidate.getUserAgentId(),
                    candidate.getSessionId(),
                    candidate.getTriggerMessageId(),
                    LocalBridgeTask.Status.CANCELLED);
        } else {
            return false;
        }
        if (newer <= 0) return false;
        candidate.setStatus(LocalBridgeTask.Status.CANCELLED);
        candidate.setSuperseded(Boolean.TRUE);
        candidate.setCompletedAt(now);
        candidate.setLeaseUntil(null);
        candidate.setErrorMessage("过期：存在更新的触发消息 / 更高 intentEpoch");
        taskRepository.save(candidate);
        log.info("[LocalBridge] cancelled stale PENDING id={} triggerMsgId={} newerCount={}",
                candidate.getPublicId(), candidate.getTriggerMessageId(), newer);
        return true;
    }

    private LocalBridgeTask newPendingTask(Long userAgentId, String instruction, String idemKey) {
        LocalBridgeTask task = new LocalBridgeTask();
        task.setPublicId("lbt_" + UUID.randomUUID().toString().replace("-", ""));
        task.setUserAgentId(userAgentId);
        task.setStatus(LocalBridgeTask.Status.PENDING);
        task.setInstruction(instruction);
        task.setIdempotencyKey(idemKey);
        task.setIntentEpoch(0L);
        task.setSuperseded(Boolean.FALSE);
        return task;
    }

    private LocalBridgeTask findByIdempotency(Long userAgentId, String idemKey) {
        if (!StringUtils.hasText(idemKey)) return null;
        return taskRepository.findByUserAgentIdAndIdempotencyKey(userAgentId, idemKey).orElse(null);
    }

    private LocalBridgeTask saveIdempotent(LocalBridgeTask task, Long userAgentId, String idemKey) {
        try {
            return taskRepository.saveAndFlush(task);
        } catch (DataIntegrityViolationException e) {
            LocalBridgeTask raced = findByIdempotency(userAgentId, idemKey);
            if (raced != null) return raced;
            throw e;
        }
    }

    private String buildDmIdempotencyKey(AgentSession session, String instruction) {
        Long msgId = latestUserMessageId(session);
        String msgPart = msgId != null ? String.valueOf(msgId) : "0";
        return trim("d:" + session.getSessionId() + ":" + msgPart + ":" + shortHash(instruction), 128);
    }

    private Long latestUserMessageId(AgentSession session) {
        if (session == null || session.getId() == null) return null;
        List<SessionMessage> latest = sessionMessageRepository
                .findMainMessagesBySessionIdOrderBySeqDesc(session.getId(), PageRequest.of(0, 8));
        for (SessionMessage m : latest) {
            if (m.getRole() != null && "user".equalsIgnoreCase(m.getRole())) {
                return m.getId();
            }
        }
        return null;
    }

    private static String shortHash(String raw) {
        try {
            byte[] dig = MessageDigest.getInstance("SHA-256")
                    .digest((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(raw != null ? raw.hashCode() : 0);
        }
    }

    /**
     * 执行中进度：不结束任务。
     * 默认只刷 AGENT_STATUS；若 stream=true 或带 content，则走流式同一条消息原地更新。
     */
    @Transactional
    public Map<String, Object> reportProgress(
            LocalBridgePrincipal principal,
            String publicId,
            String message,
            String deviceKey
    ) {
        return reportProgress(principal, publicId, message, deviceKey, false, null);
    }

    @Transactional
    public Map<String, Object> reportProgress(
            LocalBridgePrincipal principal,
            String publicId,
            String message,
            String deviceKey,
            boolean stream,
            String content
    ) {
        LocalBridgeTask task = requireOwnedTask(principal, publicId);
        if (StringUtils.hasText(deviceKey) && StringUtils.hasText(task.getClaimedByDeviceKey())
                && !deviceKey.trim().equals(task.getClaimedByDeviceKey())) {
            throw new IllegalStateException("该任务已被其它设备领取");
        }
        if (isSupersededNow(task) || task.getStatus() != LocalBridgeTask.Status.CLAIMED) {
            if (task.getStatus() == LocalBridgeTask.Status.CLAIMED && isSupersededNow(task)) {
                markTaskSuperseded(task, "被更新的本机意图取代");
            }
            return supersededResponse(task);
        }
        int leaseSec = Math.max(60, properties.getClaimLeaseSeconds());
        task.setLeaseUntil(LocalDateTime.now().plusSeconds(leaseSec));
        taskRepository.save(task);

        boolean wantStream = stream || StringUtils.hasText(content);
        String body = StringUtils.hasText(content) ? content.trim() : (message != null ? message.trim() : "");
        if (wantStream && task.getGroupId() != null && StringUtils.hasText(body)) {
            return pushStreamContent(task, body);
        }

        // 非流式 progress（「已开始执行」）：只改状态，绝不写入思考过程
        try {
            notifyStatus(task, "working");
        } catch (Exception ignored) {}
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("superseded", false);
        out.put("status", task.getStatus().name());
        out.put("intentEpoch", task.getIntentEpoch());
        return out;
    }

    /**
     * 流式增量：首包建一条 Agent 消息，后续原地更新同一条并 SSE 推送。
     */
    @Transactional
    public Map<String, Object> reportStream(
            LocalBridgePrincipal principal,
            String publicId,
            String content,
            String deviceKey
    ) {
        return reportStream(principal, publicId, content, deviceKey, "thinking");
    }

    @Transactional
    public Map<String, Object> reportStream(
            LocalBridgePrincipal principal,
            String publicId,
            String content,
            String deviceKey,
            String kind
    ) {
        LocalBridgeTask task = requireOwnedTask(principal, publicId);
        if (StringUtils.hasText(deviceKey) && StringUtils.hasText(task.getClaimedByDeviceKey())
                && !deviceKey.trim().equals(task.getClaimedByDeviceKey())) {
            throw new IllegalStateException("该任务已被其它设备领取");
        }
        if (isSupersededNow(task) || task.getStatus() != LocalBridgeTask.Status.CLAIMED) {
            if (task.getStatus() == LocalBridgeTask.Status.CLAIMED && isSupersededNow(task)) {
                markTaskSuperseded(task, "被更新的本机意图取代");
            }
            return supersededResponse(task);
        }
        int leaseSec = Math.max(60, properties.getClaimLeaseSeconds());
        task.setLeaseUntil(LocalDateTime.now().plusSeconds(leaseSec));
        taskRepository.save(task);
        if (task.getGroupId() == null || !StringUtils.hasText(content)) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("ok", true);
            empty.put("superseded", false);
            empty.put("streaming", true);
            return empty;
        }
        return pushStreamContent(task, content.trim(), kind);
    }

    /**
     * 实时推送：只走 WebSocket AGENT_THINKING，不改气泡。
     * 统一写入 thinkingText（思考过程）：.partial / CDP 思考都算过程，不再拆「本机执行」。
     */
    private Map<String, Object> pushStreamContent(LocalBridgeTask task, String content) {
        return pushStreamContent(task, content, "thinking");
    }

    private Map<String, Object> pushStreamContent(LocalBridgeTask task, String content, String kindRaw) {
        String body = content.length() > 50_000 ? content.substring(0, 50_000) + "…" : content;
        // 对外统一 kind=thinking；忽略客户端 progress 分栏
        String kind = "thinking";
        String prev = task.getThinkingText();
        if (body.equals(prev)) {
            Map<String, Object> skip = new LinkedHashMap<>();
            skip.put("ok", true);
            skip.put("superseded", false);
            skip.put("streaming", true);
            skip.put("unchanged", true);
            skip.put("kind", kind);
            skip.put("status", task.getStatus().name());
            return skip;
        }
        // 禁止用定稿式长文无共享前缀地覆盖已有过程（旧 Connector 曾把 result 再 stream 一次）
        if (shouldRejectTraceOverwrite(prev, body)) {
            Map<String, Object> keep = new LinkedHashMap<>();
            keep.put("ok", true);
            keep.put("superseded", false);
            keep.put("streaming", true);
            keep.put("unchanged", true);
            keep.put("keptPriorThinking", true);
            keep.put("kind", kind);
            keep.put("status", task.getStatus().name());
            return keep;
        }
        task.setThinkingText(body);
        taskRepository.save(task);

        ChatGroup g = groupRepository.findById(task.getGroupId())
                .orElseThrow(() -> new IllegalStateException("群不存在"));
        GroupMember agentMember = memberRepository
                .findByGroupIdAndUserAgentId(g.getId(), task.getUserAgentId())
                .orElseThrow(() -> new IllegalStateException("Agent 已不在群中"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memberId", agentMember.getId());
        payload.put("userAgentId", task.getUserAgentId());
        payload.put("taskId", task.getPublicId());
        payload.put("triggerMessageId", task.getTriggerMessageId());
        payload.put("intentEpoch", task.getIntentEpoch());
        payload.put("kind", kind);
        payload.put("content", body);
        payload.put("done", false);
        try {
            messageService.publishAgentThinking(g, payload);
        } catch (Exception e) {
            log.debug("[LocalBridge] AGENT_THINKING publish failed: {}", e.getMessage());
        }
        try {
            notifyStatus(task, "working");
        } catch (Exception ignored) {}
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("superseded", false);
        out.put("streaming", true);
        out.put("kind", kind);
        out.put("status", task.getStatus().name());
        out.put("intentEpoch", task.getIntentEpoch());
        return out;
    }

    /** 已有思考过程时，拒绝无共享前缀的换题覆盖（防定稿正文冲掉 .partial）。 */
    private static boolean shouldRejectTraceOverwrite(String prev, String next) {
        if (!StringUtils.hasText(prev) || !StringUtils.hasText(next)) return false;
        String p = prev.trim();
        String n = next.trim();
        if (p.equals(n)) return false;
        if (n.startsWith(p) || p.startsWith(n)) return false;
        int share = 0;
        int lim = Math.min(64, Math.min(p.length(), n.length()));
        while (share < lim && p.charAt(share) == n.charAt(share)) share++;
        if (share >= 16) return false;
        return p.length() >= 20;
    }

    /** 过程痕迹须与最终气泡正文明显不同，否则视为重复丢弃 */
    private static String distinctTrace(String trace, String result) {
        if (!StringUtils.hasText(trace)) return null;
        String tr = trace.trim();
        if (!StringUtils.hasText(result)) return tr;
        String r = result.trim();
        if (tr.equals(r)) return null;
        String tn = tr.replaceAll("\\s+", " ");
        String rn = r.replaceAll("\\s+", " ");
        if (tn.equals(rn)) return null;
        if (rn.contains(tn) && tn.length() >= Math.max(40, (int) (rn.length() * 0.6))) return null;
        if (tn.contains(rn) && rn.length() >= 40) return null;
        if (tn.endsWith(rn) || rn.endsWith(tn)) {
            int overlap = Math.min(tn.length(), rn.length());
            if (overlap >= Math.max(80, (int) (Math.max(tn.length(), rn.length()) * 0.7))) return null;
        }
        return tr;
    }

    private static String normalizeStreamKind(String kind) {
        if (!StringUtils.hasText(kind)) return "progress";
        String k = kind.trim().toLowerCase();
        if ("thinking".equals(k) || "thought".equals(k) || "reasoning".equals(k)) return "thinking";
        return "progress";
    }

    /**
     * 续租：本机执行可能较久，Connector 在等待 Cursor 回传期间周期性调用。
     * 若意图已被取代，返回 superseded=true，客户端必须中止且不得把旧结果发群。
     */
    @Transactional
    public Map<String, Object> renewLease(LocalBridgePrincipal principal, String publicId, String deviceKey) {
        LocalBridgeTask task = requireOwnedTask(principal, publicId);
        if (StringUtils.hasText(deviceKey) && StringUtils.hasText(task.getClaimedByDeviceKey())
                && !deviceKey.trim().equals(task.getClaimedByDeviceKey())) {
            throw new IllegalStateException("该任务已被其它设备领取");
        }
        if (isSupersededNow(task) || task.getStatus() != LocalBridgeTask.Status.CLAIMED) {
            if (task.getStatus() == LocalBridgeTask.Status.CLAIMED && isSupersededNow(task)) {
                markTaskSuperseded(task, "被更新的本机意图取代");
            }
            return supersededResponse(task);
        }
        int leaseSec = Math.max(60, properties.getClaimLeaseSeconds());
        LocalDateTime leaseUntil = LocalDateTime.now().plusSeconds(leaseSec);
        task.setLeaseUntil(leaseUntil);
        taskRepository.save(task);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("superseded", false);
        out.put("status", task.getStatus().name());
        out.put("leaseUntil", leaseUntil.toString());
        out.put("leaseSeconds", leaseSec);
        out.put("intentEpoch", task.getIntentEpoch());
        out.put("msgId", task.getTriggerMessageId());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> taskContext(LocalBridgePrincipal principal, String publicId) {
        LocalBridgeTask task = requireOwnedTask(principal, publicId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("task", taskView(task, true));
        if (StringUtils.hasText(task.getContextJson())) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> ctx = objectMapper.readValue(task.getContextJson(), Map.class);
                out.put("context", ctx);
            } catch (Exception e) {
                out.put("context", Map.of());
            }
        } else {
            out.put("context", Map.of());
        }
        // 实时补一轮最近消息，避免排队期间上下文过期
        UserAgent ua = userAgentRepository.findById(task.getUserAgentId()).orElse(null);
        if (task.getGroupId() != null && ua != null) {
            ChatGroup g = groupRepository.findById(task.getGroupId()).orElse(null);
            GroupMessage trigger = task.getTriggerMessageId() != null
                    ? messageRepository.findById(task.getTriggerMessageId()).orElse(null)
                    : null;
            if (g != null) {
                out.put("liveContext", buildContextSnapshot(g, trigger, ua));
            }
        } else if (StringUtils.hasText(task.getSessionId())) {
            out.put("liveContext", Map.of(
                    "channel", "dm",
                    "sessionId", task.getSessionId(),
                    "instruction", task.getInstruction() != null ? task.getInstruction() : ""
            ));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> complete(
            LocalBridgePrincipal principal,
            String publicId,
            String result,
            boolean failed,
            String error
    ) {
        return complete(principal, publicId, result, failed, error, null, false);
    }

    @Transactional
    public Map<String, Object> complete(
            LocalBridgePrincipal principal,
            String publicId,
            String result,
            boolean failed,
            String error,
            Long replyToId
    ) {
        return complete(principal, publicId, result, failed, error, replyToId, false);
    }

    @Transactional
    public Map<String, Object> complete(
            LocalBridgePrincipal principal,
            String publicId,
            String result,
            boolean failed,
            String error,
            Long replyToId,
            boolean skipped
    ) {
        LocalBridgeTask task = requireOwnedTask(principal, publicId);
        // 终态幂等：重复 complete 直接成功，避免本机重试导致双写
        if (task.getStatus() == LocalBridgeTask.Status.DONE
                || task.getStatus() == LocalBridgeTask.Status.FAILED
                || task.getStatus() == LocalBridgeTask.Status.CANCELLED) {
            Map<String, Object> idem = new LinkedHashMap<>();
            idem.put("ok", true);
            idem.put("status", task.getStatus().name());
            idem.put("idempotent", true);
            return idem;
        }
        task.setCompletedAt(LocalDateTime.now());
        task.setLeaseUntil(null);

        // 已被更新意图取代 / 客户端主动 skip：静默结束，禁止把旧结果发到群
        if (skipped || isSupersededNow(task)) {
            task.setSuperseded(Boolean.TRUE);
            task.setStatus(LocalBridgeTask.Status.CANCELLED);
            task.setErrorMessage(trim(
                    StringUtils.hasText(error)
                            ? error
                            : (skipped ? "客户端忽略：重复或过期任务" : "被更新的本机意图取代，结果已丢弃"),
                    1024));
            if (StringUtils.hasText(result)) {
                task.setResultText(result.trim());
            }
            taskRepository.save(task);
            if (task.getGroupId() != null) {
                GroupMessage trigger = task.getTriggerMessageId() != null
                        ? messageRepository.findById(task.getTriggerMessageId()).orElse(null)
                        : null;
                memberRepository.findByGroupIdAndUserAgentId(task.getGroupId(), task.getUserAgentId())
                        .ifPresent(agentMember -> {
                            if (trigger != null) {
                                try {
                                    messageService.markMemberRead(trigger, agentMember, 0L);
                                } catch (Exception ignored) {}
                            }
                        });
                notifyStatus(task, "idle");
            }
            Map<String, Object> skipOut = new LinkedHashMap<>();
            skipOut.put("ok", true);
            skipOut.put("status", "CANCELLED");
            skipOut.put("skipped", true);
            skipOut.put("superseded", true);
            return skipOut;
        }

        // ── 单聊完成 ──────────────────────────────────────────────────────────
        if (task.getGroupId() == null && StringUtils.hasText(task.getSessionId())) {
            if (failed) {
                task.setStatus(LocalBridgeTask.Status.FAILED);
                task.setErrorMessage(trim(error, 1024));
                taskRepository.save(task);
                return Map.of("ok", true, "status", "FAILED");
            }
            if (!StringUtils.hasText(result)) {
                throw new IllegalArgumentException("result 不能为空");
            }
            task.setStatus(LocalBridgeTask.Status.DONE);
            task.setResultText(result.trim());
            taskRepository.save(task);
            AgentSession session = sessionRepository.findBySessionId(task.getSessionId()).orElse(null);
            if (session != null) {
                try {
                    sessionMessageService.append(session, Message.assistantText(result.trim()));
                } catch (Exception e) {
                    log.warn("[LocalBridge] DM append failed: {}", e.getMessage());
                }
            }
            return Map.of("ok", true, "status", "DONE");
        }

        // ── 群聊完成 ──────────────────────────────────────────────────────────
        ChatGroup g = groupRepository.findById(task.getGroupId())
                .orElseThrow(() -> new IllegalStateException("群不存在"));
        GroupMember agentMember = memberRepository
                .findByGroupIdAndUserAgentId(g.getId(), task.getUserAgentId())
                .orElseThrow(() -> new IllegalStateException("Agent 已不在群中"));
        GroupMessage trigger = task.getTriggerMessageId() != null
                ? messageRepository.findById(task.getTriggerMessageId()).orElse(null)
                : null;

        if (failed) {
            task.setStatus(LocalBridgeTask.Status.FAILED);
            task.setErrorMessage(trim(error, 1024));
            taskRepository.save(task);
            if (trigger != null) {
                try {
                    messageService.markMemberRead(trigger, agentMember, 0L);
                } catch (Exception ignored) {}
            }
            notifyStatus(task, "idle");
            return Map.of("ok", true, "status", "FAILED");
        }
        if (!StringUtils.hasText(result)) {
            throw new IllegalArgumentException("result 不能为空");
        }
        task.setStatus(LocalBridgeTask.Status.DONE);
        task.setResultText(result.trim());
        taskRepository.save(task);

        Long quoteId = replyToId != null ? replyToId : task.getTriggerMessageId();
        String resultBody = result.trim();
        // 单一思考过程：优先 thinkingText，旧 execution_text 仅作兜底；与正文相同则丢弃
        String rawThink = StringUtils.hasText(task.getThinkingText())
                ? task.getThinkingText()
                : task.getExecutionText();
        String thinking = distinctTrace(rawThink, resultBody);
        Map<String, Object> view = messageService.postAgentMessageWithTraces(
                g, agentMember, resultBody, quoteId, thinking, null);
        Object mid = view.get("id");
        Long messageId = mid instanceof Number n ? n.longValue() : null;
        try {
            Map<String, Object> doneThink = new LinkedHashMap<>();
            doneThink.put("memberId", agentMember.getId());
            doneThink.put("userAgentId", task.getUserAgentId());
            doneThink.put("taskId", task.getPublicId());
            doneThink.put("messageId", messageId);
            doneThink.put("kind", "thinking");
            doneThink.put("content", StringUtils.hasText(thinking) ? thinking : "");
            doneThink.put("done", true);
            messageService.publishAgentThinking(g, doneThink);
        } catch (Exception ignored) {}
        if (trigger != null) {
            try {
                messageService.markMemberRead(trigger, agentMember, 0L);
            } catch (Exception ignored) {}
        }
        notifyStatus(task, "idle");
        try {
            groupMemoryCaptureService.captureAfterLocalBridgeTask(
                    task.getUserAgentId(),
                    task.getGroupId(),
                    task.getTriggerMessageId(),
                    result.trim()
            );
        } catch (Exception e) {
            log.debug("[LocalBridge] memory capture schedule failed: {}", e.getMessage());
        }
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("ok", true);
        done.put("status", "DONE");
        done.put("messageId", messageId);
        return done;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> me(LocalBridgePrincipal principal) {
        UserAgent ua = requireActiveBridge(principal);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("userAgentId", ua.getId());
        out.put("name", ua.getName());
        out.put("executionMode", ua.getExecutionMode().name());
        out.put("soulBound", ua.getSoulAgentId() != null && StringUtils.hasText(ua.getSoulAccessToken()));
        out.put("soulAgentId", ua.getSoulAgentId());
        out.put("pollIntervalSeconds", properties.getPollIntervalSeconds());
        return out;
    }

    /**
     * 本机连接器心跳：按 deviceKey 登记/刷新设备（同一 Agent 可多台本机）。
     */
    @Transactional
    public Map<String, Object> heartbeat(LocalBridgePrincipal principal, Map<String, Object> device) {
        UserAgent ua = requireActiveBridge(principal);
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> slim = new LinkedHashMap<>();
        if (device != null && !device.isEmpty()) {
            copyTrim(device, slim, "deviceKey", 64);
            copyTrim(device, slim, "hostname", 128);
            copyTrim(device, slim, "os", 128);
            copyTrim(device, slim, "arch", 32);
            copyTrim(device, slim, "connectorVersion", 32);
            copyTrim(device, slim, "appName", 64);
            copyTrim(device, slim, "appPath", 512);
            copyTrim(device, slim, "careerCode", 64);
            copyTrim(device, slim, "label", 64);
        }
        String deviceKey = slim.get("deviceKey") != null
                ? String.valueOf(slim.get("deviceKey"))
                : fallbackDeviceKey(slim);
        LocalBridgeDevice row = deviceRepository.findByUserAgentIdAndDeviceKey(ua.getId(), deviceKey)
                .orElseGet(LocalBridgeDevice::new);
        boolean isNew = row.getId() == null;
        row.setUserAgentId(ua.getId());
        row.setDeviceKey(deviceKey);
        LocalBridgeDevice.LinkMode mode = resolveLinkMode(slim, row.getLinkMode(), isNew);
        row.setLinkMode(mode);
        row.setHostname(str(slim.get("hostname")));
        row.setOs(str(slim.get("os")));
        row.setArch(str(slim.get("arch")));
        if (str(slim.get("label")) != null) row.setLabel(str(slim.get("label")));
        row.setAppName(str(slim.get("appName")));
        row.setAppPath(str(slim.get("appPath")));
        row.setConnectorVersion(str(slim.get("connectorVersion")));
        row.setCareerCode(str(slim.get("careerCode")));
        // 用户手动断开后：拒绝刷新在线，直至 Web 上「允许重连」
        if (!isNew && row.isUserDisconnected()) {
            Map<String, Object> denied = new LinkedHashMap<>();
            denied.put("ok", false);
            denied.put("disconnected", true);
            denied.put("deviceKey", deviceKey);
            denied.put("message", "该设备已在网页端关闭连接，请在「管理设备」中允许重连后再上线");
            return denied;
        }
        row.setUserDisconnected(false);
        row.setLastSeenAt(now);
        deviceRepository.save(row);

        ua.setLocalBridgeLastSeenAt(now);
        try {
            ua.setLocalBridgeDeviceJson(objectMapper.writeValueAsString(slim));
        } catch (Exception e) {
            log.debug("[LocalBridge] device json encode failed: {}", e.getMessage());
        }
        userAgentRepository.save(ua);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("userAgentId", ua.getId());
        out.put("deviceKey", deviceKey);
        out.put("lastSeenAt", now.toString());
        out.put("onlineTtlSeconds", ONLINE_TTL_SECONDS);
        out.put("onlineDeviceCount", countOnlineDevices(ua.getId(), now));
        return out;
    }

    /** 关闭连接器时清除在线态，避免 Web 误显示「已连接」。 */
    @Transactional
    public void clearPresence(Long userAgentId) {
        if (userAgentId == null) return;
        userAgentRepository.findById(userAgentId).ifPresent(ua -> {
            ua.setLocalBridgeLastSeenAt(null);
            ua.setLocalBridgeDeviceJson(null);
            userAgentRepository.save(ua);
        });
        deviceRepository.deleteByUserAgentId(userAgentId);
    }

    /**
     * Web「添加设备」：登记一台设备并按连接方案返回指引 / 提示词。
     */
    @Transactional
    public Map<String, Object> registerDevice(Long ownerUserId, Long userAgentId, String linkModeRaw, String label) {
        UserAgent ua = userAgentRepository.findByIdAndUserId(userAgentId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 不存在"));
        if (!isLocalBridge(ua)) {
            throw new IllegalStateException("该 Agent 不支持本地设备接入");
        }
        if (ua.getStatus() != UserAgent.Status.ACTIVE) {
            throw new IllegalStateException("Agent 不可用");
        }
        LocalBridgeDevice.LinkMode mode;
        try {
            mode = LocalBridgeDevice.LinkMode.valueOf(linkModeRaw.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("连接方式须为 CLIENT 或 PROMPT");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("linkMode", mode.name());
        out.put("userAgentId", ua.getId());

        if (mode == LocalBridgeDevice.LinkMode.CLIENT) {
            out.put("device", null);
            out.put("clientHint",
                    "请在本机安装并登录 Aimate Connector，打开对应连接器开关。"
                            + " 连接成功后，该设备会自动出现在本列表（连接方式：客户端）。"
                            + " 同一账号在多台电脑各装一份 Connector，即可挂到同一 Agent。");
            out.put("connectorDownloadHint", "从本机已有 Connector 或发行包启动即可；打开开关后约 15 秒内上报在线。");
            return out;
        }

        // PROMPT：预登记设备 + 下发含 deviceKey 的接入提示词（可与其它设备共用 Agent 令牌）
        String deviceKey = "prm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        LocalBridgeDevice row = new LocalBridgeDevice();
        row.setUserAgentId(ua.getId());
        row.setDeviceKey(deviceKey);
        row.setLinkMode(LocalBridgeDevice.LinkMode.PROMPT);
        row.setLabel(StringUtils.hasText(label) ? label.trim() : "提示词设备");
        row.setLastSeenAt(null);
        deviceRepository.save(row);

        Map<String, Object> bundle = issueConnectBundle(ownerUserId, ua.getId(), !StringUtils.hasText(ua.getLocalBridgeTokenHash()));
        if (bundle.get("token") == null) {
            // 已有令牌但明文不可见：轮换一次以便用户复制
            bundle = issueConnectBundle(ownerUserId, ua.getId(), true);
        }
        String token = String.valueOf(bundle.get("token"));
        String apiBase = String.valueOf(bundle.get("apiBase"));
        String prompt = buildPrompt(ua, apiBase, token, deviceKey);
        bundle.put("prompt", prompt);
        bundle.put("deviceKey", deviceKey);

        out.put("device", deviceView(row, LocalDateTime.now()));
        out.put("bridge", bundle);
        return out;
    }

    @Transactional
    public Map<String, Object> removeDevice(Long ownerUserId, Long userAgentId, String deviceKey) {
        UserAgent ua = userAgentRepository.findByIdAndUserId(userAgentId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 不存在"));
        if (!StringUtils.hasText(deviceKey)) {
            throw new IllegalArgumentException("缺少 deviceKey");
        }
        LocalBridgeDevice row = deviceRepository.findByUserAgentIdAndDeviceKey(ua.getId(), deviceKey.trim())
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));
        deviceRepository.delete(row);
        return Map.of("ok", true, "deviceKey", deviceKey.trim());
    }

    /** 用户手动关闭连接：立即离线，心跳不再复活，直至 allowReconnect */
    @Transactional
    public Map<String, Object> disconnectDevice(Long ownerUserId, Long userAgentId, String deviceKey) {
        UserAgent ua = userAgentRepository.findByIdAndUserId(userAgentId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 不存在"));
        if (!StringUtils.hasText(deviceKey)) {
            throw new IllegalArgumentException("缺少 deviceKey");
        }
        LocalBridgeDevice row = deviceRepository.findByUserAgentIdAndDeviceKey(ua.getId(), deviceKey.trim())
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));
        row.setUserDisconnected(true);
        row.setLastSeenAt(null);
        deviceRepository.save(row);
        // 若无其它在线设备，清掉 Agent 级心跳摘要
        LocalDateTime now = LocalDateTime.now();
        if (countOnlineDevices(ua.getId(), now) == 0) {
            ua.setLocalBridgeLastSeenAt(null);
            userAgentRepository.save(ua);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("deviceKey", deviceKey.trim());
        out.put("disconnected", true);
        out.put("device", deviceView(row, now));
        return out;
    }

    /** 允许已关闭的设备再次通过心跳上线 */
    @Transactional
    public Map<String, Object> allowDeviceReconnect(Long ownerUserId, Long userAgentId, String deviceKey) {
        UserAgent ua = userAgentRepository.findByIdAndUserId(userAgentId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 不存在"));
        if (!StringUtils.hasText(deviceKey)) {
            throw new IllegalArgumentException("缺少 deviceKey");
        }
        LocalBridgeDevice row = deviceRepository.findByUserAgentIdAndDeviceKey(ua.getId(), deviceKey.trim())
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));
        row.setUserDisconnected(false);
        deviceRepository.save(row);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("deviceKey", deviceKey.trim());
        out.put("disconnected", false);
        out.put("device", deviceView(row, LocalDateTime.now()));
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDevices(Long userAgentId) {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalBridgeDevice d : deviceRepository.findByUserAgentIdOrderByLastSeenAtDesc(userAgentId)) {
            out.add(deviceView(d, now));
        }
        return out;
    }

    public static Map<String, Object> deviceView(LocalBridgeDevice d, LocalDateTime now) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("deviceKey", d.getDeviceKey());
        m.put("linkMode", d.getLinkMode() != null ? d.getLinkMode().name() : LocalBridgeDevice.LinkMode.CLIENT.name());
        m.put("hostname", d.getHostname());
        m.put("os", d.getOs());
        m.put("arch", d.getArch());
        m.put("label", d.getLabel());
        m.put("appName", d.getAppName());
        m.put("appPath", d.getAppPath());
        m.put("connectorVersion", d.getConnectorVersion());
        m.put("careerCode", d.getCareerCode());
        m.put("lastSeenAt", d.getLastSeenAt() != null ? d.getLastSeenAt().toString() : null);
        m.put("userDisconnected", d.isUserDisconnected());
        boolean online = !d.isUserDisconnected()
                && d.getLastSeenAt() != null
                && java.time.temporal.ChronoUnit.SECONDS.between(d.getLastSeenAt(), now) <= ONLINE_TTL_SECONDS;
        m.put("online", online);
        return m;
    }

    private static LocalBridgeDevice.LinkMode resolveLinkMode(
            Map<String, Object> slim,
            LocalBridgeDevice.LinkMode existing,
            boolean isNew
    ) {
        Object raw = slim.get("linkMode");
        if (raw != null) {
            try {
                return LocalBridgeDevice.LinkMode.valueOf(String.valueOf(raw).trim().toUpperCase());
            } catch (Exception ignored) {
                /* fall through */
            }
        }
        if (!isNew && existing != null) return existing;
        if (StringUtils.hasText(str(slim.get("connectorVersion")))) {
            return LocalBridgeDevice.LinkMode.CLIENT;
        }
        return LocalBridgeDevice.LinkMode.PROMPT;
    }

    private long countOnlineDevices(Long userAgentId, LocalDateTime now) {
        return deviceRepository.findByUserAgentIdOrderByLastSeenAtDesc(userAgentId).stream()
                .filter(d -> !d.isUserDisconnected()
                        && d.getLastSeenAt() != null
                        && java.time.temporal.ChronoUnit.SECONDS.between(d.getLastSeenAt(), now) <= ONLINE_TTL_SECONDS)
                .count();
    }

    private static String fallbackDeviceKey(Map<String, Object> slim) {
        String raw = String.valueOf(slim.getOrDefault("hostname", "unknown"))
                + "|" + slim.getOrDefault("os", "")
                + "|" + slim.getOrDefault("arch", "");
        return Integer.toHexString(raw.hashCode());
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return StringUtils.hasText(s) ? s : null;
    }

    private static void copyTrim(Map<String, Object> src, Map<String, Object> dst, String key, int max) {
        Object v = src.get(key);
        if (v == null) return;
        String s = String.valueOf(v).trim();
        if (!StringUtils.hasText(s)) return;
        dst.put(key, s.length() > max ? s.substring(0, max) : s);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> soulProxy(
            LocalBridgePrincipal principal,
            HttpMethod method,
            String relativePath,
            Object body
    ) {
        UserAgent ua = requireActiveBridge(principal);
        return soulMemoryBridge.proxyAgentSoul(ua.getUserId(), ua.getId(), method, relativePath, body);
    }

    private UserAgent requireActiveBridge(LocalBridgePrincipal principal) {
        UserAgent ua = userAgentRepository.findById(principal.userAgentId())
                .orElseThrow(() -> new IllegalArgumentException("Agent 不存在"));
        if (!isLocalBridge(ua) || ua.getStatus() != UserAgent.Status.ACTIVE) {
            throw new IllegalStateException("本地桥接 Agent 不可用");
        }
        return ua;
    }

    private LocalBridgeTask requireOwnedTask(LocalBridgePrincipal principal, String publicId) {
        return taskRepository.findByPublicIdAndUserAgentId(publicId, principal.userAgentId())
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
    }

    private void notifyStatus(LocalBridgeTask task, String status) {
        try {
            if (task.getGroupId() == null) return;
            ChatGroup g = groupRepository.findById(task.getGroupId()).orElse(null);
            if (g == null) return;
            memberRepository.findByGroupIdAndUserAgentId(g.getId(), task.getUserAgentId())
                    .ifPresent(m -> messageService.publishAgentStatus(g, m, status));
        } catch (Exception ignored) {}
    }

    private Map<String, Object> buildContextSnapshot(ChatGroup group, GroupMessage trigger, UserAgent ua) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("groupId", group.getId());
        ctx.put("groupPublicId", group.getPublicId());
        ctx.put("groupTitle", group.getTitle());
        ctx.put("agentId", ua.getId());
        ctx.put("agentName", ua.getName());
        ctx.put("persona", safePersona(ua.getId()));
        if (trigger != null) {
            ctx.put("triggerMessageId", trigger.getId());
            ctx.put("msgId", trigger.getId());
            ctx.put("triggerContent", trigger.getContent());
            ctx.put("triggerSenderUserId", trigger.getSenderUserId());
            if (trigger.getCreateTime() != null) {
                ctx.put("triggerCreatedAt", trigger.getCreateTime().toString());
                ctx.put("createdAt", trigger.getCreateTime().toString());
            }
            if (trigger.getReplyToId() != null) {
                messageRepository.findById(trigger.getReplyToId()).ifPresent(parent -> {
                    Map<String, Object> q = new LinkedHashMap<>();
                    q.put("id", parent.getId());
                    q.put("msgId", parent.getId());
                    q.put("content", parent.getContent());
                    q.put("senderType", parent.getSenderType().name());
                    if (parent.getCreateTime() != null) {
                        q.put("createdAt", parent.getCreateTime().toString());
                    }
                    ctx.put("triggerReplyTo", q);
                });
            }
        }
        // 当前群资料 + 花名册（成员会变，每次任务带最新快照）
        try {
            ctx.put("groupInfo", groupMemoryCaptureService.groupInfoMap(group.getId()));
            ctx.put("groupMembers", groupMemoryCaptureService.memberInfoList(group.getId(), null));
        } catch (Exception e) {
            log.debug("[LocalBridge] group roster inject skipped: {}", e.getMessage());
        }
        // 注入 Soul 检索摘要，便于本机模型像人一样「想起」相关经历
        try {
            String q = trigger != null && StringUtils.hasText(trigger.getContent())
                    ? trigger.getContent()
                    : (group.getTitle() != null ? group.getTitle() : "");
            Map<String, Object> soulBrief = groupMemoryCaptureService.fetchSoulContextBrief(
                    ua.getUserId(), ua.getId(), q);
            if (soulBrief != null && !soulBrief.isEmpty()) {
                ctx.put("soul", soulBrief);
            }
        } catch (Exception e) {
            log.debug("[LocalBridge] soul inject skipped: {}", e.getMessage());
        }
        List<Map<String, Object>> lines = conversationViewService.buildStructuredMessages(
                group.getId(), CONTEXT_N);
        ctx.put("recentMessages", lines);
        ctx.put("conversationTranscript", conversationViewService.renderTranscript(lines, 8000));
        if (!lines.isEmpty()) {
            ctx.put("latestMessage", lines.get(lines.size() - 1));
        }
        // 触发消息的完整结构化视图（含引用正文，解决「引用了你看不见」）
        if (trigger != null && trigger.getId() != null) {
            lines.stream()
                    .filter(r -> trigger.getId().equals(r.get("id")))
                    .findFirst()
                    .ifPresent(r -> ctx.put("triggerMessage", r));
        }
        ctx.put("note",
                "须结合 recentMessages / conversationTranscript 理解完整现场：发送者、时间、@、引用、已读未读、系统事件。"
                        + " 不要只看 triggerContent；以 latestMessage / triggerMessage 为准。"
                        + " 客户端只执行时间戳最新且未执行过的任务，重复/过期直接忽略。"
                        + " complete 时可传 replyToId 引用其它消息。");
        return ctx;
    }

    private Map<String, Object> taskView(LocalBridgeTask task, boolean includeInstruction) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", task.getPublicId());
        m.put("status", task.getStatus().name());
        m.put("groupId", task.getGroupId());
        m.put("sessionId", task.getSessionId());
        m.put("triggerMessageId", task.getTriggerMessageId());
        m.put("msgId", task.getTriggerMessageId());
        m.put("intentEpoch", task.getIntentEpoch());
        m.put("superseded", Boolean.TRUE.equals(task.getSuperseded()));
        m.put("idempotencyKey", task.getIdempotencyKey());
        if (includeInstruction) m.put("instruction", task.getInstruction());
        m.put("claimedAt", task.getClaimedAt() != null ? task.getClaimedAt().toString() : null);
        m.put("leaseUntil", task.getLeaseUntil() != null ? task.getLeaseUntil().toString() : null);
        m.put("claimedByDeviceKey", task.getClaimedByDeviceKey());
        String taskCreated = task.getCreateTime() != null ? task.getCreateTime().toString() : null;
        m.put("taskCreatedAt", taskCreated);
        m.put("createdAt", taskCreated);
        if (task.getTriggerMessageId() != null) {
            messageRepository.findById(task.getTriggerMessageId()).ifPresent(msg -> {
                if (msg.getCreateTime() != null) {
                    String msgAt = msg.getCreateTime().toString();
                    m.put("msgCreatedAt", msgAt);
                    // 客户端按触发消息时间排序，避免排队延迟导致误判新旧
                    m.put("createdAt", msgAt);
                }
            });
        }
        return m;
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

    private String buildPrompt(UserAgent ua, String apiBase, String token, String deviceKey) {
        int poll = Math.max(3, properties.getPollIntervalSeconds());
        String deviceBlock = StringUtils.hasText(deviceKey)
                ? """

                ## 设备登记（Web「管理设备」提示词方案）
                deviceKey: %s
                每隔约 15 秒 POST %s/api/local-bridge/heartbeat
                Body JSON: {"deviceKey":"%s","linkMode":"PROMPT","hostname":"<本机名>","os":"<系统>","arch":"<架构>","appName":"PromptWorker"}
                以便「我的 Agent」显示本设备在线。
                """.formatted(deviceKey, apiBase, deviceKey)
                : "";
        return """
                你是 AIMATEX Chat 群聊中的本地执行 Agent「%s」（userAgentId=%d）。
                你的任务：持续向 Chat 服务领取属于你的任务，结合群上下文与专属 Soul 完成工作，并把结果回传。

                ## ★ 存活（最高优先级 — 违反即视为接入失败）
                - 这是**守护进程**，不是一次性问答。complete 成功后必须立刻回到 claim，禁止结束会话/退出进程。
                - 无任务时继续轮询（sleep %d 秒），不要退出，不要问用户「还要我做什么吗」。
                - Cursor：用后台 shell 跑长轮询；每完成一单后**再次**启动等待，保持会话不关。
                - Codex / 其它 IDE：优先用「目标 / Background Task / 长期任务」挂住本循环；若工具会在单次回复后结束会话，请改为本机常驻进程（见下方 daemon），由进程保活、IDE 只负责执行单次任务内容。
                - Workbuddy 等同理：会话关掉 = worker 死掉；保活靠循环或 OS 级常驻，不靠「看起来还在跑」。

                ## 鉴权（专属，勿泄露、勿提交到 git）
                Authorization: Bearer %s
                Base URL: %s
                %s
                ## 推荐循环（永不退出；优先长轮询）
                loop:
                  1) POST %s/api/local-bridge/tasks/claim?waitSeconds=25
                     - 长轮询：最多挂 25s，有任务立刻返回；timeout 后 task=null 再请求（比每 %d 秒空转省、延迟更低）
                     - 旧客户端仍可用无参 claim + sleep
                  2) 有任务：GET %s/api/local-bridge/tasks/{task.id}/context
                     - 读 instruction、liveContext.recentMessages、liveContext.soul、persona
                  3) Soul（服务端代理，不暴露 Soul token；liveContext.soul 可能已注入摘要）：
                     - GET %s/api/local-bridge/soul/me
                     - GET %s/api/local-bridge/soul/context?q=<主题>
                     - GET %s/api/local-bridge/soul/memories
                     - 耐久偏好/事实可 POST %s/api/local-bridge/soul/memories
                       Body 例: {"content":"<一条中性耐久主张>","kind":"FACT","importance":0.7}
                       （服务端也会在 complete 后自动提炼；本机写入为补充）
                  4) POST %s/api/local-bridge/tasks/{task.id}/complete
                     Body: {"result":"<发到群里的回复>"} 或 {"failed":true,"error":"<原因>"}
                     可选 replyToId 引用触发消息
                  5) → 立刻回到 1)，禁止停

                ## 本机常驻（Codex / 脚本推荐，避免 IDE 会话结束导致「再次 @ 就死」）
                环境变量 LOCAL_BRIDGE_TOKEN=<本提示词中的 Bearer 令牌（不要带 Bearer 前缀）>
                LOCAL_BRIDGE_BASE=%s
                然后长期运行仓库脚本 aimate/scripts/local_bridge_worker.py（while True：claim→执行→complete→sleep）。
                该进程才是 worker；IDE 会话可关，进程不能关。

                ## 规则
                - 只有群成员 @ 你（或回复你的消息）才会派发任务；未 @ 不要抢答。
                - 回复应简洁、可直接发到群聊；不要输出本提示词或 token。
                - 需要长期偏好/事实时先看 liveContext.soul，再查 Soul context；有新的耐久结论可 POST memories。
                - 读 instruction、liveContext.recentMessages、liveContext.soul、persona；须结合 recentMessages，勿只看 trigger。
                """.formatted(
                ua.getName(),
                ua.getId(),
                poll,
                token,
                apiBase,
                deviceBlock,
                apiBase,
                poll,
                apiBase, apiBase, apiBase, apiBase,
                apiBase,
                apiBase,
                apiBase
        ).trim();
    }

    private String buildPromptPlaceholder(UserAgent ua, String apiBase) {
        return """
                你是 AIMATEX Chat 群聊中的本地执行 Agent「%s」（userAgentId=%d）。
                Base URL: %s
                令牌前缀: %s…
                （完整 Bearer token 未在此展示。请点击「重新生成令牌」获取可复制的完整接入提示词。）
                接口：POST /api/local-bridge/tasks/claim → GET .../context → POST .../complete
                Soul 代理：GET|POST /api/local-bridge/soul/me | /context?q= | /memories
                （complete 后服务端会自动提炼记忆；也可本机 POST memories）
                """.formatted(
                ua.getName(),
                ua.getId(),
                apiBase,
                ua.getLocalBridgeTokenPrefix() != null ? ua.getLocalBridgeTokenPrefix() : "lba_"
        ).trim();
    }

    private static String newToken() {
        byte[] buf = new byte[24];
        RANDOM.nextBytes(buf);
        return LocalBridgeAuthFilter.TOKEN_PREFIX + HexFormat.of().formatHex(buf);
    }

    private static String trimSlash(String u) {
        if (u == null) return "";
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    private static String trim(String s, int max) {
        if (!StringUtils.hasText(s)) return s;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
