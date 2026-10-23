package com.openforge.aimate.group;

import com.openforge.aimate.agent.AgentPromptService;
import com.openforge.aimate.agent.SystemModelChatHelper;
import com.openforge.aimate.domain.ChatGroup;
import com.openforge.aimate.domain.GroupMember;
import com.openforge.aimate.domain.GroupMessage;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.llm.LlmRouter;
import com.openforge.aimate.llm.model.ChatRequest;
import com.openforge.aimate.llm.model.ChatResponse;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.localbridge.LocalBridgeService;
import com.openforge.aimate.office.AgentDailyPointsService;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.repository.GroupMemberRepository;
import com.openforge.aimate.repository.GroupMessageRepository;
import com.openforge.aimate.repository.SystemModelRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import com.openforge.aimate.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GroupAgentDispatcher {

    private static final int CONTEXT_N = 40;
    /** SMART 投票分析消耗 */
    private static final long VOTE_POINTS = 1L;
    /** 生成回复分析消耗 */
    private static final long GENERATE_POINTS = 5L;
    private static final Pattern MENTION = Pattern.compile("@([^\\s@，,。.！!？?]+)");

    /** LLM 投票结果：未调用模型则 analyzed=false（不算已读） */
    private record VoteOutcome(boolean analyzed, boolean yes, long pointsSpent) {
        static VoteOutcome skipped() { return new VoteOutcome(false, false, 0L); }
        static VoteOutcome of(boolean yes, long pts) { return new VoteOutcome(true, yes, pts); }
    }

    private record GenOutcome(String content, long pointsSpent) {}


    private final GroupMemberRepository memberRepository;
    private final GroupMessageRepository messageRepository;
    private final UserAgentRepository userAgentRepository;
    private final UserRepository userRepository;
    private final AgentPromptService agentPromptService;
    private final SystemModelRepository systemModelRepository;
    private final SystemModelChatHelper systemModelChatHelper;
    private final LlmRouter llmRouter;
    private final AgentDailyPointsService dailyPointsService;
    private final GroupMessageService messageService;
    private final LocalBridgeService localBridgeService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    /** 单 Agent 串行：避免叠话 */
    private final ConcurrentHashMap<Long, Semaphore> agentLocks = new ConcurrentHashMap<>();

    public GroupAgentDispatcher(
            GroupMemberRepository memberRepository,
            GroupMessageRepository messageRepository,
            UserAgentRepository userAgentRepository,
            UserRepository userRepository,
            AgentPromptService agentPromptService,
            SystemModelRepository systemModelRepository,
            SystemModelChatHelper systemModelChatHelper,
            LlmRouter llmRouter,
            AgentDailyPointsService dailyPointsService,
            @Lazy GroupMessageService messageService,
            LocalBridgeService localBridgeService
    ) {
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.userAgentRepository = userAgentRepository;
        this.userRepository = userRepository;
        this.agentPromptService = agentPromptService;
        this.systemModelRepository = systemModelRepository;
        this.systemModelChatHelper = systemModelChatHelper;
        this.llmRouter = llmRouter;
        this.dailyPointsService = dailyPointsService;
        this.messageService = messageService;
        this.localBridgeService = localBridgeService;
    }

    public void dispatchAfterUserMessage(ChatGroup group, GroupMessage userMsg, GroupMember sender) {
        List<GroupMember> agents = memberRepository.findByGroupIdAndMemberType(
                group.getId(), GroupMember.MemberType.AGENT);
        String roster = buildRosterText(group.getId());
        String dialogue = buildRecentDialogue(group.getId(), userMsg);

        List<GroupMember> smartAgents = new ArrayList<>();
        for (GroupMember am : agents) {
            GroupMember.AgentReplyMode mode = effectiveMode(am);
            if (mode == GroupMember.AgentReplyMode.SMART) {
                smartAgents.add(am);
            } else {
                executor.submit(() -> handleOne(group, userMsg, sender, am, roster, true, 0L));
            }
        }
        if (!smartAgents.isEmpty()) {
            executor.submit(() -> dispatchSmartAgents(group, userMsg, sender, smartAgents, roster, dialogue));
        }
    }

    /**
     * SMART 两阶段：每个 Agent 用花名册+对话各自问大模型要不要回 → 再生成。
     * 多人同时 YES 可并行开口，不强制单人。
     */
    private void dispatchSmartAgents(
            ChatGroup group,
            GroupMessage userMsg,
            GroupMember sender,
            List<GroupMember> smartAgents,
            String roster,
            String dialogue
    ) {
        // 已投票 YES 的 Agent → 生成；投票消耗记在 value 里，生成后再累计已读积分
        Map<Long, Long> votePointsByMemberId = new ConcurrentHashMap<>();
        List<GroupMember> speakers = new CopyOnWriteArrayList<>();
        Phaser phaser = new Phaser(1);
        for (GroupMember am : smartAgents) {
            phaser.register();
            executor.submit(() -> {
                try {
                    if (isMentionedOrReply(userMsg, am)) {
                        votePointsByMemberId.put(am.getId(), 0L);
                        speakers.add(am);
                        return;
                    }
                    VoteOutcome vote = voteShouldReply(group, userMsg, am, roster, dialogue);
                    if (!vote.analyzed()) {
                        // 未做大模型分析：不算已读
                        return;
                    }
                    if (vote.yes()) {
                        votePointsByMemberId.put(am.getId(), vote.pointsSpent());
                        speakers.add(am);
                    } else {
                        // 分析后决定不回：已读，展示投票消耗
                        messageService.markMemberRead(userMsg, am, vote.pointsSpent());
                        messageService.publishAgentStatus(group, am, "read_only");
                    }
                } catch (Exception e) {
                    log.warn("[GroupAgent] SMART vote path failed: {}", e.getMessage());
                } finally {
                    phaser.arriveAndDeregister();
                }
            });
        }
        phaser.arriveAndAwaitAdvance();

        for (GroupMember am : speakers) {
            long prior = votePointsByMemberId.getOrDefault(am.getId(), 0L);
            executor.submit(() -> handleOne(group, userMsg, sender, am, roster, false, prior));
        }
    }

    private GroupMember.AgentReplyMode effectiveMode(GroupMember agentMember) {
        GroupMember.AgentReplyMode mode = agentMember.getAgentReplyMode() != null
                ? agentMember.getAgentReplyMode()
                : GroupMember.AgentReplyMode.MENTION_ONLY;
        UserAgent ua = userAgentRepository.findById(agentMember.getUserAgentId()).orElse(null);
        if (LocalBridgeService.isLocalBridge(ua) && mode != GroupMember.AgentReplyMode.SILENT) {
            return GroupMember.AgentReplyMode.MENTION_ONLY;
        }
        return mode;
    }

    /**
     * @param needDecide true=非 SMART 两阶段路径，内部再判定是否开口
     * @param priorPoints 此前已消耗（如 SMART 投票）的积分，计入已读展示
     */
    private void handleOne(
            ChatGroup group,
            GroupMessage userMsg,
            GroupMember sender,
            GroupMember agentMember,
            String roster,
            boolean needDecide,
            long priorPoints
    ) {
        Semaphore lock = agentLocks.computeIfAbsent(agentMember.getUserAgentId(), id -> new Semaphore(1));
        if (!lock.tryAcquire()) {
            // 未抢到锁=本轮未分析，不算已读
            return;
        }
        long analyzedPoints = Math.max(0L, priorPoints);
        try {
            UserAgent uaEarly = userAgentRepository.findById(agentMember.getUserAgentId()).orElse(null);
            boolean localBridge = LocalBridgeService.isLocalBridge(uaEarly);
            GroupMember.AgentReplyMode mode = effectiveMode(agentMember);

            boolean shouldSpeak;
            if (!needDecide) {
                shouldSpeak = true;
            } else {
                shouldSpeak = switch (mode) {
                    case SILENT -> false;
                    case ALWAYS -> true;
                    case MENTION_ONLY -> isMentionedOrReply(userMsg, agentMember);
                    case SMART -> {
                        if (isMentionedOrReply(userMsg, agentMember)) {
                            yield true;
                        }
                        VoteOutcome vote = voteShouldReply(
                                group, userMsg, agentMember, roster, buildRecentDialogue(group.getId(), userMsg));
                        if (vote.analyzed()) {
                            analyzedPoints += vote.pointsSpent();
                            yield vote.yes();
                        }
                        // 未分析：不算已读
                        yield false;
                    }
                };
            }

            if (!shouldSpeak) {
                // 仅当已做过 LLM 分析（如 SMART 投 NO）才记已读；SILENT / 未 @ 不记
                if (analyzedPoints > 0) {
                    messageService.markMemberRead(userMsg, agentMember, analyzedPoints);
                    messageService.publishAgentStatus(group, agentMember, "read_only");
                }
                return;
            }

            if (localBridge && uaEarly != null) {
                // 本地桥：等 worker complete 后再记已读（分析在本地完成）
                localBridgeService.enqueueFromMention(group, userMsg, agentMember, uaEarly);
                return;
            }

            messageService.publishAgentStatus(group, agentMember, "thinking");
            GenOutcome gen = generateReply(group, userMsg, agentMember, roster);
            analyzedPoints += gen.pointsSpent();
            if (StringUtils.hasText(gen.content())) {
                messageService.postAgentMessage(group, agentMember, gen.content().trim(), userMsg.getId());
            }
            // Agent 已读 = 已完成大模型分析；悬浮展示 analyzedPoints
            if (analyzedPoints > 0) {
                messageService.markMemberRead(userMsg, agentMember, analyzedPoints);
            }
            messageService.publishAgentStatus(group, agentMember, "idle");
        } catch (Exception e) {
            log.warn("[GroupAgent] agent={} group={} failed: {}",
                    agentMember.getUserAgentId(), group.getPublicId(), e.getMessage());
            try {
                if (analyzedPoints > 0) {
                    messageService.markMemberRead(userMsg, agentMember, analyzedPoints);
                }
                messageService.publishAgentStatus(group, agentMember, "idle");
            } catch (Exception ignored) {}
        } finally {
            lock.release();
        }
    }

    private boolean isMentionedOrReply(GroupMessage userMsg, GroupMember agentMember) {
        UserAgent ua = userAgentRepository.findById(agentMember.getUserAgentId()).orElse(null);
        if (ua == null) return false;
        if (userMsg.getReplyToId() != null) {
            GroupMessage parent = messageRepository.findById(userMsg.getReplyToId()).orElse(null);
            if (parent != null
                    && parent.getSenderType() == GroupMessage.SenderType.AGENT
                    && agentMember.getUserAgentId().equals(parent.getSenderAgentId())) {
                return true;
            }
        }
        String content = userMsg.getContent();
        if (!StringUtils.hasText(content)) return false;
        String name = ua.getName() != null ? ua.getName().trim() : "";
        if (name.isEmpty()) return false;
        String lower = content.toLowerCase(Locale.ROOT);
        if (lower.contains("@" + name.toLowerCase(Locale.ROOT))) return true;
        // 宽松：@ 后片段匹配名字前缀
        var m = MENTION.matcher(content);
        while (m.find()) {
            String token = m.group(1).trim();
            if (name.equalsIgnoreCase(token) || name.toLowerCase(Locale.ROOT).startsWith(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 每个 SMART Agent 各自带着花名册 + 近期对话问大模型：本轮该不该我开口。
     * 未调用模型（额度不足 / 失败）返回 skipped，不算已读。
     */
    private VoteOutcome voteShouldReply(
            ChatGroup group,
            GroupMessage userMsg,
            GroupMember agentMember,
            String roster,
            String dialogue
    ) {
        UserAgent ua = userAgentRepository.findById(agentMember.getUserAgentId()).orElse(null);
        if (ua == null) return VoteOutcome.skipped();
        try {
            dailyPointsService.assertWithinDailyLimit(ua.getId(), VOTE_POINTS);
        } catch (Exception e) {
            log.info("[GroupAgent] SMART skip agent={} daily limit: {}", ua.getId(), e.getMessage());
            return VoteOutcome.skipped();
        }
        String raw = userMsg.getContent() == null ? "" : userMsg.getContent().trim();
        String persona = safePersona(ua.getId());
        String system = """
                # Role
                你是群聊里的「%s」。现在只做一件事：根据花名册与对话，判断**你自己**本轮要不要说话。
                只输出 YES 或 NO。

                # Decision policy
                1. 对方在点名/称呼花名册里的**别人**（哪怕没有 @）→ 你必须 NO，不要抢话、不要代答
                2. 对方在点名/称呼的就是你（全名或明显指向你的身份称呼）→ YES
                3. 话题明显落在你的人设/职责，且没有更合适的在场同事 → YES
                4. 话题更适合花名册里另一位同事 → NO
                5. 纯闲聊/打招呼：你可以 YES（想接就接），也可以 NO；按人设自然选择
                6. 纯系统通知 / 无信息 → NO
                7. 拿不准且有更对口的同事在场 → NO；群里只有你能接 → YES

                # Output contract
                YES | NO
                """.formatted(ua.getName()).stripIndent();
        String user = """
                # Group
                %s

                # Roster（群里所有人，用他们的真实名字判断点名）
                %s

                # You
                - name: %s
                - kind: %s
                - persona: %s

                # Recent dialogue（时间正序；最后一条是最新用户消息）
                %s

                # Latest user message（请据此投票）
                %s
                """.formatted(
                group.getTitle() != null ? group.getTitle() : "",
                StringUtils.hasText(roster) ? roster : "（暂无）",
                ua.getName(),
                ua.getKind() != null ? ua.getKind().name() : "AGENT",
                persona.length() > 500 ? persona.substring(0, 500) : persona,
                StringUtils.hasText(dialogue) ? dialogue : "(no prior)",
                raw
        );
        try {
            String answer;
            SystemModel model = pickCheapModel(ua);
            if (model != null) {
                answer = systemModelChatHelper.chatText(model, system, user);
            } else {
                ChatResponse resp = llmRouter.chat(ChatRequest.builder()
                        .messages(List.of(Message.system(system), Message.user(user)))
                        .temperature(0.0)
                        .maxTokens(8)
                        .build());
                answer = resp.firstMessage() != null ? resp.firstMessage().content() : "";
            }
            dailyPointsService.recordSpend(ua.getUserId(), ua.getId(), VOTE_POINTS);
            boolean yes = answer != null && answer.toUpperCase(Locale.ROOT).contains("YES");
            log.info("[GroupAgent] SMART vote agent={} answer={} msg={}",
                    ua.getName(), answer != null ? answer.trim() : "", clip(raw, 40));
            return VoteOutcome.of(yes, VOTE_POINTS);
        } catch (Exception e) {
            log.warn("[GroupAgent] SMART vote failed agent={}: {}", ua.getId(), e.getMessage());
            return VoteOutcome.skipped();
        }
    }

    private String buildRecentDialogue(Long groupId, GroupMessage latest) {
        List<GroupMessage> recent = messageRepository.findByGroupIdOrderByIdDesc(
                groupId, PageRequest.of(0, Math.min(CONTEXT_N, 24)));
        List<GroupMessage> chronological = new ArrayList<>(recent);
        java.util.Collections.reverse(chronological);
        boolean hasLatest = false;
        List<String> lines = new ArrayList<>();
        for (GroupMessage m : chronological) {
            if (m.getSenderType() == GroupMessage.SenderType.SYSTEM) continue;
            if (latest != null && latest.getId() != null && latest.getId().equals(m.getId())) {
                hasLatest = true;
            }
            lines.add(formatLine(m));
        }
        if (latest != null && !hasLatest) {
            lines.add(formatLine(latest));
        }
        String joined = String.join("\n", lines);
        if (joined.length() > 3500) {
            return joined.substring(joined.length() - 3500);
        }
        return joined;
    }

    private String buildRosterText(Long groupId) {
        List<GroupMember> members = memberRepository.findByGroupIdOrderByIdAsc(groupId);
        List<String> lines = new ArrayList<>();
        for (GroupMember m : members) {
            if (m.getMemberType() == GroupMember.MemberType.USER) {
                String label = "用户";
                if (StringUtils.hasText(m.getNickname())) {
                    label = m.getNickname().trim();
                } else if (m.getUserId() != null) {
                    User u = userRepository.findById(m.getUserId()).orElse(null);
                    if (u != null) {
                        if (StringUtils.hasText(u.getDisplayName())) label = u.getDisplayName().trim();
                        else if (StringUtils.hasText(u.getUsername())) label = u.getUsername().trim();
                    }
                }
                lines.add("- 人类：「" + label + "」");
            } else if (m.getMemberType() == GroupMember.MemberType.AGENT && m.getUserAgentId() != null) {
                userAgentRepository.findById(m.getUserAgentId()).ifPresent(ua -> {
                    String kind = ua.getKind() == UserAgent.Kind.SECRETARY ? "秘书同事" : "专业同事";
                    String digest = safePersona(ua.getId());
                    if (digest.length() > 80) digest = digest.substring(0, 80) + "…";
                    lines.add("- 同事：「" + ua.getName() + "」（" + kind
                            + (StringUtils.hasText(digest) ? "；简介：" + digest : "")
                            + "）");
                });
            }
        }
        return String.join("\n", lines);
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /**
     * 群聊回复系统提示：结构化契约 + 拟人表达约束。
     * 人设文本作为「角色卡」注入，不把 Agent 写成冷冰冰的机器人旁白。
     */
    private static String buildReplySystemPrompt(UserAgent ua, ChatGroup group, String persona, String roster) {
        String kindLabel = ua.getKind() == UserAgent.Kind.SECRETARY ? "秘书同事" : "专业同事";
        String personaBlock = StringUtils.hasText(persona) ? persona.trim() : "（未单独配置人设，按可靠、好相处的同事来）";
        String rosterBlock = StringUtils.hasText(roster) ? roster.trim() : "（暂无花名册）";
        return """
                # Identity
                你是群「%s」里的真人同事「%s」（%s）。你有自己的性格、专业边界和说话习惯。
                在群里你就是这个人——不是客服话术机，也不是旁白解说员。

                # Group roster（必须知晓，用于判断谁该说话）
                %s

                # Character card（必须遵守）
                %s

                # How you talk（拟人）
                - 像微信/飞书群里正常说话：口语、有温度，可适度幽默或吐槽，但别油腻。
                - 用「我」，直接对话；禁止「作为 AI / 语言模型 / 智能助手」等自我介绍。
                - 先接住对方情绪或话题，再给观点或建议；不要一上来列长清单。
                - 闲聊就闲聊，别硬转工作；对方认真提问再认真答。
                - 若对方点名的是花名册里别人，不要替他们长篇作答；最多一句「让某某来接」或闭嘴由调度决定。
                - 话题落在你职责内时，直接以第一人称回答，不要把问题甩给别人。
                - 可以点名群友（@名字），但别刷屏、别连环追问。

                # Reply contract
                - 默认 1～3 句（约 30～120 字）；复杂问题最多一小段，仍要好读。
                - 只输出要发到群里的正文，不要标题、不要「回复：」前缀、不要 Markdown 大标题。
                - 若信息不足，用一句自然的追问补齐，而不是拒绝或空话。
                - 保持人设一致；不确定时宁可简短真诚，也不要编造事实。

                # Context usage
                下文会按时间给出近期群聊；把最新一条用户消息当作本轮要回应的内容。
                """.formatted(
                group.getTitle() != null ? group.getTitle() : "这个群",
                ua.getName(),
                kindLabel,
                rosterBlock,
                personaBlock
        ).stripIndent();
    }

    private GenOutcome generateReply(ChatGroup group, GroupMessage userMsg, GroupMember agentMember, String roster) {
        UserAgent ua = userAgentRepository.findById(agentMember.getUserAgentId()).orElse(null);
        if (ua == null) return new GenOutcome(null, 0L);
        try {
            dailyPointsService.assertWithinDailyLimit(ua.getId(), GENERATE_POINTS);
        } catch (Exception e) {
            // 未调用模型
            return new GenOutcome("今天额度用完了，我先歇会儿，晚点再聊～", 0L);
        }

        String persona = safePersona(ua.getId());
        List<GroupMessage> recent = messageRepository.findByGroupIdOrderByIdDesc(
                group.getId(), PageRequest.of(0, CONTEXT_N));
        List<GroupMessage> chronological = new ArrayList<>(recent);
        java.util.Collections.reverse(chronological);

        List<Message> msgs = new ArrayList<>();
        msgs.add(Message.system(buildReplySystemPrompt(ua, group, persona, roster)));

        for (GroupMessage m : chronological) {
            if (m.getId().equals(userMsg.getId())) continue;
            String line = formatLine(m);
            if (m.getSenderType() == GroupMessage.SenderType.AGENT
                    && ua.getId().equals(m.getSenderAgentId())) {
                msgs.add(Message.assistantText(m.getContent() != null ? m.getContent() : ""));
            } else if (m.getSenderType() != GroupMessage.SenderType.SYSTEM) {
                msgs.add(Message.user(line));
            }
        }
        msgs.add(Message.user(formatLine(userMsg)));

        try {
            String content;
            SystemModel model = pickCheapModel(ua);
            if (model != null) {
                content = systemModelChatHelper.chatMessages(model, msgs, 0.7, 1024);
            } else {
                ChatResponse resp = llmRouter.chat(ChatRequest.builder()
                        .messages(msgs)
                        .temperature(0.7)
                        .maxTokens(1024)
                        .build());
                content = resp.firstMessage() != null ? resp.firstMessage().content() : null;
            }
            // 只要完成了一次模型调用，就算分析消耗（无论是否空回复）
            dailyPointsService.recordSpend(ua.getUserId(), ua.getId(), GENERATE_POINTS);
            return new GenOutcome(content, GENERATE_POINTS);
        } catch (Exception e) {
            log.warn("[GroupAgent] generate failed: {}", e.getMessage());
            return new GenOutcome(humanizeGenerateError(e.getMessage()), 0L);
        }
    }

    private static String humanizeGenerateError(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "刚才卡住了一下，你再说一遍？";
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (t.contains("401") || t.contains("authentication") || t.contains("api key")) {
            return "我这边模型钥匙好像失效了，麻烦管理员看一下配置～";
        }
        if (t.contains("timeout") || t.contains("timed out")) {
            return "网络有点慢，我晚半步再回你。";
        }
        return "刚才没接住，稍后再试一次呗。";
    }

    private String formatLine(GroupMessage m) {
        String who = switch (m.getSenderType()) {
            case USER -> "用户#" + m.getSenderUserId();
            case AGENT -> "Agent#" + m.getSenderAgentId();
            case SYSTEM -> "系统";
        };
        if (m.getSenderType() == GroupMessage.SenderType.AGENT && m.getSenderAgentId() != null) {
            who = userAgentRepository.findById(m.getSenderAgentId()).map(UserAgent::getName).orElse(who);
        }
        return who + ": " + (m.getContent() != null ? m.getContent() : "");
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

    private SystemModel pickCheapModel(UserAgent ua) {
        if (ua.getDefaultSystemModelId() != null) {
            SystemModel preferred = systemModelRepository.findById(ua.getDefaultSystemModelId()).orElse(null);
            if (preferred != null && preferred.isEnabled()) {
                return preferred;
            }
        }
        List<SystemModel> enabled = systemModelRepository.findByEnabledTrueOrderBySortOrderAsc();
        return enabled.isEmpty() ? null : enabled.get(0);
    }
}
