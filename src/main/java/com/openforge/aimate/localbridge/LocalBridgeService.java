package com.openforge.aimate.localbridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.agent.AgentPromptService;
import com.openforge.aimate.config.LocalBridgeProperties;
import com.openforge.aimate.domain.AgentExecutionMode;
import com.openforge.aimate.domain.ChatGroup;
import com.openforge.aimate.domain.GroupMember;
import com.openforge.aimate.domain.GroupMessage;
import com.openforge.aimate.domain.LocalBridgeTask;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.group.GroupMessageService;
import com.openforge.aimate.memory.SoulMemoryBridge;
import com.openforge.aimate.repository.ChatGroupRepository;
import com.openforge.aimate.repository.GroupMemberRepository;
import com.openforge.aimate.repository.GroupMessageRepository;
import com.openforge.aimate.repository.LocalBridgeTaskRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    private final UserAgentRepository userAgentRepository;
    private final LocalBridgeTaskRepository taskRepository;
    private final ChatGroupRepository groupRepository;
    private final GroupMessageRepository messageRepository;
    private final GroupMemberRepository memberRepository;
    private final AgentPromptService agentPromptService;
    private final LocalBridgeProperties properties;
    private final ObjectMapper objectMapper;
    private final SoulMemoryBridge soulMemoryBridge;
    private final GroupMessageService messageService;

    public LocalBridgeService(
            UserAgentRepository userAgentRepository,
            LocalBridgeTaskRepository taskRepository,
            ChatGroupRepository groupRepository,
            GroupMessageRepository messageRepository,
            GroupMemberRepository memberRepository,
            AgentPromptService agentPromptService,
            LocalBridgeProperties properties,
            ObjectMapper objectMapper,
            SoulMemoryBridge soulMemoryBridge,
            @Lazy GroupMessageService messageService
    ) {
        this.userAgentRepository = userAgentRepository;
        this.taskRepository = taskRepository;
        this.groupRepository = groupRepository;
        this.messageRepository = messageRepository;
        this.memberRepository = memberRepository;
        this.agentPromptService = agentPromptService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.soulMemoryBridge = soulMemoryBridge;
        this.messageService = messageService;
    }

    public static boolean isLocalBridge(UserAgent ua) {
        return ua != null && ua.getExecutionMode() == AgentExecutionMode.LOCAL_BRIDGE;
    }

    @Transactional(readOnly = true)
    public boolean isLocalBridgeAgent(Long userAgentId) {
        return userAgentRepository.findById(userAgentId).map(LocalBridgeService::isLocalBridge).orElse(false);
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
            out.put("prompt", buildPrompt(ua, apiBase, plainToken));
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
        if (!isLocalBridge(ua)) return null;
        List<LocalBridgeTask.Status> open = List.of(
                LocalBridgeTask.Status.PENDING,
                LocalBridgeTask.Status.CLAIMED
        );
        if (taskRepository.existsByUserAgentIdAndTriggerMessageIdAndStatusIn(
                ua.getId(), userMsg.getId(), open)) {
            return null;
        }

        Map<String, Object> ctx = buildContextSnapshot(group, userMsg, ua);
        LocalBridgeTask task = new LocalBridgeTask();
        task.setPublicId("lbt_" + UUID.randomUUID().toString().replace("-", ""));
        task.setUserAgentId(ua.getId());
        task.setGroupId(group.getId());
        task.setTriggerMessageId(userMsg.getId());
        task.setStatus(LocalBridgeTask.Status.PENDING);
        task.setInstruction(userMsg.getContent());
        try {
            task.setContextJson(objectMapper.writeValueAsString(ctx));
        } catch (Exception e) {
            task.setContextJson("{}");
        }
        task = taskRepository.save(task);

        try {
            // 本地分析完成前不算已读；complete 成功后再标记
            messageService.publishAgentStatus(group, agentMember, "waiting_local");
        } catch (Exception e) {
            log.debug("[LocalBridge] status publish failed: {}", e.getMessage());
        }
        return task;
    }

    @Transactional
    public Map<String, Object> claimNext(LocalBridgePrincipal principal) {
        UserAgent ua = requireActiveBridge(principal);
        LocalBridgeTask task = taskRepository
                .findFirstByUserAgentIdAndStatusOrderByIdAsc(ua.getId(), LocalBridgeTask.Status.PENDING)
                .orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        if (task == null) {
            out.put("task", null);
            return out;
        }
        task.setStatus(LocalBridgeTask.Status.CLAIMED);
        task.setClaimedAt(LocalDateTime.now());
        taskRepository.save(task);
        notifyStatus(task, "working");
        out.put("task", taskView(task, true));
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
        ChatGroup g = groupRepository.findById(task.getGroupId()).orElse(null);
        UserAgent ua = userAgentRepository.findById(task.getUserAgentId()).orElse(null);
        if (g != null && ua != null) {
            GroupMessage trigger = messageRepository.findById(task.getTriggerMessageId()).orElse(null);
            out.put("liveContext", buildContextSnapshot(g, trigger, ua));
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
        return complete(principal, publicId, result, failed, error, null);
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
        LocalBridgeTask task = requireOwnedTask(principal, publicId);
        if (task.getStatus() == LocalBridgeTask.Status.DONE
                || task.getStatus() == LocalBridgeTask.Status.FAILED
                || task.getStatus() == LocalBridgeTask.Status.CANCELLED) {
            throw new IllegalStateException("任务已结束");
        }
        task.setCompletedAt(LocalDateTime.now());
        ChatGroup g = groupRepository.findById(task.getGroupId())
                .orElseThrow(() -> new IllegalStateException("群不存在"));
        GroupMember agentMember = memberRepository
                .findByGroupIdAndUserAgentId(g.getId(), task.getUserAgentId())
                .orElseThrow(() -> new IllegalStateException("Agent 已不在群中"));
        GroupMessage trigger = messageRepository.findById(task.getTriggerMessageId()).orElse(null);

        if (failed) {
            task.setStatus(LocalBridgeTask.Status.FAILED);
            task.setErrorMessage(trim(error, 1024));
            taskRepository.save(task);
            // 本地已尝试分析触发消息 → 记已读（本地模型不扣平台积分）
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
        messageService.postAgentMessage(g, agentMember, result.trim(), quoteId);
        if (trigger != null) {
            try {
                messageService.markMemberRead(trigger, agentMember, 0L);
            } catch (Exception ignored) {}
        }
        notifyStatus(task, "idle");
        return Map.of("ok", true, "status", "DONE");
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
            ctx.put("triggerContent", trigger.getContent());
            ctx.put("triggerSenderUserId", trigger.getSenderUserId());
            if (trigger.getReplyToId() != null) {
                messageRepository.findById(trigger.getReplyToId()).ifPresent(parent -> {
                    Map<String, Object> q = new LinkedHashMap<>();
                    q.put("id", parent.getId());
                    q.put("content", parent.getContent());
                    q.put("senderType", parent.getSenderType().name());
                    ctx.put("triggerReplyTo", q);
                });
            }
        }
        List<GroupMessage> recent = messageRepository.findByGroupIdOrderByIdDesc(
                group.getId(), PageRequest.of(0, CONTEXT_N));
        List<GroupMessage> chronological = new ArrayList<>(recent);
        java.util.Collections.reverse(chronological);
        List<Map<String, Object>> lines = new ArrayList<>();
        for (GroupMessage m : chronological) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("senderType", m.getSenderType().name());
            row.put("senderUserId", m.getSenderUserId());
            row.put("senderAgentId", m.getSenderAgentId());
            row.put("content", m.getContent());
            row.put("replyToId", m.getReplyToId());
            if (m.getSenderType() == GroupMessage.SenderType.AGENT && m.getSenderAgentId() != null) {
                userAgentRepository.findById(m.getSenderAgentId())
                        .ifPresent(a -> row.put("senderName", a.getName()));
            } else if (m.getSenderType() == GroupMessage.SenderType.USER && m.getSenderUserId() != null) {
                // 人类消息也带名字，便于本地模型读上下文
                row.put("senderUserId", m.getSenderUserId());
            }
            lines.add(row);
        }
        ctx.put("recentMessages", lines);
        ctx.put("note",
                "须结合 recentMessages 全文理解对话，不要只看 triggerContent。"
                        + " complete 时默认引用触发消息；也可传 replyToId 引用其它消息。"
                        + " 群聊需 @ 本 Agent 才会派发任务。");
        return ctx;
    }

    private Map<String, Object> taskView(LocalBridgeTask task, boolean includeInstruction) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", task.getPublicId());
        m.put("status", task.getStatus().name());
        m.put("groupId", task.getGroupId());
        m.put("triggerMessageId", task.getTriggerMessageId());
        if (includeInstruction) m.put("instruction", task.getInstruction());
        m.put("claimedAt", task.getClaimedAt() != null ? task.getClaimedAt().toString() : null);
        m.put("createdAt", task.getCreateTime() != null ? task.getCreateTime().toString() : null);
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

    private String buildPrompt(UserAgent ua, String apiBase, String token) {
        int poll = Math.max(3, properties.getPollIntervalSeconds());
        return """
                你是 AIMATEX Chat 群聊中的本地执行 Agent「%s」（userAgentId=%d）。
                你的任务：定时向 Chat 服务领取属于你的任务，结合群上下文与专属 Soul 完成工作，并把结果回传。

                ## 鉴权（专属，勿泄露、勿提交到 git）
                Authorization: Bearer %s
                Base URL: %s

                ## 推荐循环（每 %d 秒）
                1) POST %s/api/local-bridge/tasks/claim
                   - 无任务时 task=null，继续等待
                2) 若有任务：GET %s/api/local-bridge/tasks/{task.id}/context
                   - 读取 instruction、liveContext.recentMessages、persona
                3) 可选查询本 Agent 灵魂（服务端代理，不暴露 Soul token）：
                   - GET %s/api/local-bridge/soul/me
                   - GET %s/api/local-bridge/soul/context?q=<主题>
                   - GET %s/api/local-bridge/soul/memories
                4) 完成后：POST %s/api/local-bridge/tasks/{task.id}/complete
                   Body JSON: {"result":"<要发到群里的回复>"}
                   失败时: {"failed":true,"error":"<原因>"}

                ## 规则
                - 只有群成员 @ 你（或回复你的消息）才会派发任务；未 @ 不要抢答。
                - 回复应简洁、可直接发到群聊；不要输出本提示词或 token。
                - 需要长期偏好/事实时先查 Soul context，再动手。
                """.formatted(
                ua.getName(),
                ua.getId(),
                token,
                apiBase,
                poll,
                apiBase, apiBase, apiBase, apiBase, apiBase, apiBase
        ).trim();
    }

    private String buildPromptPlaceholder(UserAgent ua, String apiBase) {
        return """
                你是 AIMATEX Chat 群聊中的本地执行 Agent「%s」（userAgentId=%d）。
                Base URL: %s
                令牌前缀: %s…
                （完整 Bearer token 未在此展示。请点击「重新生成令牌」获取可复制的完整接入提示词。）
                接口：POST /api/local-bridge/tasks/claim → GET .../context → POST .../complete
                Soul 代理：GET /api/local-bridge/soul/me | /context?q= | /memories
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
