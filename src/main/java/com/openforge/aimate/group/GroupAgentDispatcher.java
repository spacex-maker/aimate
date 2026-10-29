package com.openforge.aimate.group;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.agent.AgentPromptService;
import com.openforge.aimate.agent.SystemModelChatHelper;
import com.openforge.aimate.agent.UserAgentIdentityService;
import com.openforge.aimate.agent.UserAgentService;
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
import com.openforge.aimate.memory.GroupMemoryCaptureService;
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
import java.util.Set;
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
    /** 生成本机派发判定消耗 */
    private static final long LOCAL_JUDGE_POINTS = 1L;
    /** 生成回复分析消耗 */
    private static final long GENERATE_POINTS = 5L;
    private static final Pattern MENTION = Pattern.compile("@([^\\s@，,。.！!？?]+)");

    /**
     * LLM 意图识别结果。
     * analyzed=false 表示未得到模型结论；quotaHit=true 表示日额度卡掉（前置限流）。
     */
    private record VoteOutcome(boolean analyzed, boolean yes, long pointsSpent, boolean quotaHit) {
        static VoteOutcome skipped() { return new VoteOutcome(false, false, 0L, false); }
        static VoteOutcome quotaExceeded() { return new VoteOutcome(false, false, 0L, true); }
        static VoteOutcome of(boolean yes, long pts) { return new VoteOutcome(true, yes, pts, false); }
    }

    private record GenOutcome(String content, long pointsSpent) {}

    /** 是否需要客户端/本机参与：由模型语义判断，禁止关键词表 */
    private record LocalJudgeOutcome(boolean needLocal, String instruction, long pointsSpent) {
        static LocalJudgeOutcome skipped() { return new LocalJudgeOutcome(false, null, 0L); }
        static LocalJudgeOutcome no(long pts) { return new LocalJudgeOutcome(false, null, pts); }
        static LocalJudgeOutcome yes(String instruction, long pts) {
            return new LocalJudgeOutcome(true, instruction, pts);
        }
    }

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
    private final GroupMentionApprovalService mentionApprovalService;
    private final GroupAgentContextService groupAgentContextService;
    private final GroupMemoryCaptureService groupMemoryCaptureService;
    private final GroupAgentToolService groupAgentToolService;
    private final UserAgentIdentityService userAgentIdentityService;
    private final UserAgentService userAgentService;
    private final ObjectMapper objectMapper;
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
            LocalBridgeService localBridgeService,
            @Lazy GroupMentionApprovalService mentionApprovalService,
            GroupAgentContextService groupAgentContextService,
            GroupMemoryCaptureService groupMemoryCaptureService,
            GroupAgentToolService groupAgentToolService,
            UserAgentIdentityService userAgentIdentityService,
            UserAgentService userAgentService,
            ObjectMapper objectMapper
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
        this.mentionApprovalService = mentionApprovalService;
        this.groupAgentContextService = groupAgentContextService;
        this.groupMemoryCaptureService = groupMemoryCaptureService;
        this.groupAgentToolService = groupAgentToolService;
        this.userAgentIdentityService = userAgentIdentityService;
        this.userAgentService = userAgentService;
        this.objectMapper = objectMapper;
    }

    /** 主人同意点名请示后继续开口（异步）。 */
    public void resumeAfterMentionApproved(ChatGroup group, GroupMessage trigger, GroupMember agentMember) {
        String roster = buildRosterText(group.getId());
        executor.submit(() -> handleOne(
                group, trigger, null, agentMember, roster,
                false, 0L, true, true));
    }

    /** 用户发消息后派发（兼容入口）。 */
    public void dispatchAfterUserMessage(ChatGroup group, GroupMessage userMsg, GroupMember sender) {
        dispatchAfterMessage(group, userMsg, sender);
    }

    /**
     * 群内出现新消息后派发：用户消息与 Agent 消息均走此路径。
     * SMART / MENTION / ALWAYS 均参与识别；发信 Agent 自己跳过，避免自激。
     */
    public void dispatchAfterMessage(ChatGroup group, GroupMessage triggerMsg, GroupMember sender) {
        if (triggerMsg == null || triggerMsg.getSenderType() == GroupMessage.SenderType.SYSTEM) {
            return;
        }
        List<GroupMember> agents = memberRepository.findByGroupIdAndMemberType(
                group.getId(), GroupMember.MemberType.AGENT);
        String roster = buildRosterText(group.getId());

        List<GroupMember> smartAgents = new ArrayList<>();
        for (GroupMember am : agents) {
            if (isSameAgent(sender, am)) {
                continue;
            }
            // 停用/租期过期 Agent 不进意图识别与生成（按 expiresAt 落库 EXPIRED）
            UserAgent uaStatus = userAgentRepository.findById(am.getUserAgentId()).orElse(null);
            if (!userAgentService.isUsable(uaStatus)) {
                continue;
            }
            GroupMember.AgentReplyMode mode = effectiveMode(am);
            if (mode == GroupMember.AgentReplyMode.SMART) {
                smartAgents.add(am);
            } else {
                executor.submit(() -> handleOne(
                        group, triggerMsg, sender, am, roster,
                        true, 0L, false, false));
            }
        }
        if (!smartAgents.isEmpty()) {
            executor.submit(() -> dispatchSmartAgents(group, triggerMsg, sender, smartAgents, roster));
        }
    }

    private static boolean isSameAgent(GroupMember sender, GroupMember candidate) {
        if (sender == null || candidate == null) return false;
        if (sender.getId() != null && sender.getId().equals(candidate.getId())) return true;
        return sender.getUserAgentId() != null
                && sender.getUserAgentId().equals(candidate.getUserAgentId());
    }

    /**
     * SMART 全流程：每个开了智能回复的 Agent 都走大模型意图识别，再生成。
     * 前置只做去重（发信 Agent 自己已在上层跳过）与限流（日额度）；
     * @ / 软点名不做「要不要触发」的硬编码决定，只作为投票提示与审批标记。
     * 多人同时 YES 可并行开口，不强制单人。
     */
    private void dispatchSmartAgents(
            ChatGroup group,
            GroupMessage userMsg,
            GroupMember sender,
            List<GroupMember> smartAgents,
            String roster
    ) {
        Map<Long, Long> votePointsByMemberId = new ConcurrentHashMap<>();
        Map<Long, Boolean> mentionedByMemberId = new ConcurrentHashMap<>();
        List<GroupMember> speakers = new CopyOnWriteArrayList<>();
        Phaser phaser = new Phaser(1);
        for (GroupMember am : smartAgents) {
            phaser.register();
            executor.submit(() -> {
                try {
                    // 仅记录 @/引用，供后续请示审批；不在此短路决定开口
                    boolean fromMention = isMentionedOrReply(userMsg, am);
                    mentionedByMemberId.put(am.getId(), fromMention);

                    String agentDialogue = groupAgentContextService.buildDialogueText(
                            group, am.getUserAgentId(), userMsg);
                    VoteOutcome vote = voteShouldReply(group, userMsg, am, roster, agentDialogue);
                    if (vote.quotaHit()) {
                        log.info("[GroupAgent] SMART rate-limited skip agent={}", am.getUserAgentId());
                        return;
                    }
                    if (!vote.analyzed()) {
                        // 模型调用失败：不用硬编码抢答；仅当明显点到自己时兜底，避免整群哑火
                        if (fromMention || isSoftAddressed(userMsg, am)) {
                            log.warn("[GroupAgent] SMART vote failed → addressed fallback agent={}",
                                    am.getUserAgentId());
                            votePointsByMemberId.put(am.getId(), 0L);
                            speakers.add(am);
                        }
                        return;
                    }
                    if (vote.yes()) {
                        votePointsByMemberId.put(am.getId(), vote.pointsSpent());
                        speakers.add(am);
                    } else {
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
            boolean fromMention = Boolean.TRUE.equals(mentionedByMemberId.get(am.getId()));
            executor.submit(() -> handleOne(
                    group, userMsg, sender, am, roster,
                    false, prior, fromMention, false));
        }
    }

    private GroupMember.AgentReplyMode effectiveMode(GroupMember agentMember) {
        // 本机桥与云端 Agent 一致：静默 / 仅@ / 总是 / 智能相关；控本机仍由「群聊可用本机」开关决定
        return agentMember.getAgentReplyMode() != null
                ? agentMember.getAgentReplyMode()
                : GroupMember.AgentReplyMode.MENTION_ONLY;
    }

    /**
     * @param needDecide true=非 SMART 两阶段路径，内部再判定是否开口
     * @param priorPoints 此前已消耗（如 SMART 投票）的积分，计入已读展示
     * @param fromMentionHint SMART 两阶段已知是否因 @ 入选；needDecide 时会再算
     * @param skipApprovalGate 主人已同意后的续跑，跳过请示卡
     */
    private void handleOne(
            ChatGroup group,
            GroupMessage userMsg,
            GroupMember sender,
            GroupMember agentMember,
            String roster,
            boolean needDecide,
            long priorPoints,
            boolean fromMentionHint,
            boolean skipApprovalGate
    ) {
        Semaphore lock = agentLocks.computeIfAbsent(agentMember.getUserAgentId(), id -> new Semaphore(1));
        if (!lock.tryAcquire()) {
            return;
        }
        long analyzedPoints = Math.max(0L, priorPoints);
        try {
            UserAgent uaEarly = userAgentRepository.findById(agentMember.getUserAgentId()).orElse(null);
            if (!userAgentService.isUsable(uaEarly)) {
                return;
            }
            boolean localBridge = LocalBridgeService.isLocalBridge(uaEarly);
            GroupMember.AgentReplyMode mode = effectiveMode(agentMember);

            boolean fromMention = fromMentionHint;
            boolean shouldSpeak;
            if (!needDecide) {
                shouldSpeak = true;
            } else {
                shouldSpeak = switch (mode) {
                    case SILENT -> false;
                    case ALWAYS -> {
                        // 始终回，但若本轮确实被 @ 且开了请示，仍走审批
                        fromMention = isMentionedOrReply(userMsg, agentMember);
                        yield true;
                    }
                    case MENTION_ONLY -> {
                        fromMention = isMentionedOrReply(userMsg, agentMember);
                        yield fromMention;
                    }
                    case SMART -> {
                        // 与两阶段路径一致：始终走模型意图识别；@ 只作审批标记
                        fromMention = isMentionedOrReply(userMsg, agentMember);
                        VoteOutcome vote = voteShouldReply(
                                group, userMsg, agentMember, roster,
                                groupAgentContextService.buildDialogueText(
                                        group, agentMember.getUserAgentId(), userMsg));
                        if (vote.quotaHit()) {
                            yield false;
                        }
                        if (vote.analyzed()) {
                            analyzedPoints += vote.pointsSpent();
                            yield vote.yes();
                        }
                        // 模型失败兜底：仅明显点到自己时开口
                        yield fromMention || isSoftAddressed(userMsg, agentMember);
                    }
                };
            }

            if (!shouldSpeak) {
                if (analyzedPoints > 0) {
                    messageService.markMemberRead(userMsg, agentMember, analyzedPoints);
                    messageService.publishAgentStatus(group, agentMember, "read_only");
                }
                return;
            }

            if (!skipApprovalGate && fromMention && uaEarly != null
                    && mentionApprovalService.requestApprovalIfNeeded(group, userMsg, agentMember, uaEarly)) {
                return;
            }

            // 本地桥接：先云端聊天；需控本机时入队——客户端由 Connector 领取，提示词由 worker 领取
            messageService.publishAgentStatus(group, agentMember, "thinking");
            GenOutcome gen = generateReply(group, userMsg, agentMember, roster);
            analyzedPoints += gen.pointsSpent();
            String replyBody = gen.content() != null ? gen.content().trim() : "";
            String localInstruction = null;
            if (localBridge && uaEarly != null && LocalBridgeService.isGroupLocalAssistEnabled(uaEarly)) {
                LocalDispatchParsed parsed = LocalDispatchParsed.parse(replyBody);
                replyBody = parsed.chatText();
                // 无论回复是否自带 [[LOCAL:]]，都过语义判定；纯聊天不得派本机
                String dialogue = groupAgentContextService.buildDialogueText(
                        group, agentMember.getUserAgentId(), userMsg);
                LocalJudgeOutcome judge = judgeLocalDispatch(
                        group, userMsg, uaEarly, replyBody, dialogue);
                analyzedPoints += judge.pointsSpent();
                if (judge.needLocal()) {
                    if (StringUtils.hasText(judge.instruction())) {
                        localInstruction = judge.instruction().trim();
                    } else if (StringUtils.hasText(parsed.localInstruction())) {
                        localInstruction = parsed.localInstruction().trim();
                    }
                }
                // judge=false → 丢弃 [[LOCAL:]]，避免误派
            }
            if (StringUtils.hasText(replyBody)) {
                messageService.postAgentMessage(group, agentMember, replyBody, userMsg.getId());
            }
            boolean dispatchedLocal = false;
            if (StringUtils.hasText(localInstruction) && uaEarly != null) {
                var task = localBridgeService.enqueueFromMention(
                        group, userMsg, agentMember, uaEarly, localInstruction);
                dispatchedLocal = task != null;
                if (dispatchedLocal) {
                    try {
                        messageService.postAgentMessage(
                                group, agentMember,
                                "（" + localBridgeService.describeDispatchHint(uaEarly) + "）",
                                userMsg.getId());
                    } catch (Exception ignored) {}
                }
            }
            // 已派发本机任务：等设备 complete 再记已读；否则本轮记已读
            if (!dispatchedLocal && analyzedPoints > 0) {
                messageService.markMemberRead(userMsg, agentMember, analyzedPoints);
            }
            messageService.publishAgentStatus(group, agentMember,
                    dispatchedLocal ? "waiting_local" : "idle");
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

    /** 各本机职业专属花名：@Workbuddy / 说 workbuddy 只点到 WORKBUDDY 桥，不与 Cursor 抢答。 */
    private static final Map<String, List<String>> CAREER_DESKTOP_ALIASES = Map.of(
            "CURSOR", List.of("cursor"),
            "WORKBUDDY", List.of("workbuddy", "work buddy", "工作伙伴"),
            "WINDSURF", List.of("windsurf"),
            "CODEX", List.of("codex", "chatgpt"),
            "CLINE", List.of("cline")
    );

    /** 共享弱别名：仅当群里只有一只本机桥时可用，多桥并存时禁止，避免互相抢单。 */
    private static final List<String> LOCAL_DESKTOP_SHARED_ALIASES = List.of("本机", "connector");

    /**
     * 有 @ 但正文点到真名/花名/本机工具别名（至少 2 字）——仅作意图识别提示与失败兜底，
     * 不再单独决定是否开口。本机桥：用户叫「cursor/workbuddy」而档案名不同也算点到。
     */
    private boolean isSoftAddressed(GroupMessage userMsg, GroupMember agentMember) {
        if (userMsg == null || userMsg.getSenderType() != GroupMessage.SenderType.USER) return false;
        String content = userMsg.getContent();
        if (!StringUtils.hasText(content)) return false;
        UserAgent ua = userAgentRepository.findById(agentMember.getUserAgentId()).orElse(null);
        if (ua == null) return false;
        // 已明确 @ 了别人则不算软点名（对照本 Agent 全部别名）
        if (contentHasExplicitAtOfSomeoneElse(content, agentMember, ua)) return false;
        String lower = content.toLowerCase(Locale.ROOT);
        for (String alias : addressAliases(agentMember, ua)) {
            if (!StringUtils.hasText(alias) || alias.length() < 2) continue;
            if (lower.contains(alias.toLowerCase(Locale.ROOT))) {
                return canTriggerMention(userMsg, agentMember, ua);
            }
        }
        return false;
    }

    /** 群内 LOCAL_BRIDGE Agent 数量；>1 时禁用「本机/connector」共享别名。 */
    private int countLocalBridgeAgentsInGroup(Long groupId) {
        if (groupId == null) return 0;
        int n = 0;
        for (GroupMember m : memberRepository.findByGroupIdOrderByIdAsc(groupId)) {
            if (m.getMemberType() != GroupMember.MemberType.AGENT || m.getUserAgentId() == null) continue;
            UserAgent ua = userAgentRepository.findById(m.getUserAgentId()).orElse(null);
            if (LocalBridgeService.isLocalBridge(ua)) n++;
        }
        return n;
    }

    /**
     * 可被点名的别名：真名、群昵称、人设里的桌面工具名、本机桥「按职业」花名。
     * 原则：一只本机 Agent 只认自己的 career 花名，不与其它桌面 AI 混淆。
     */
    private List<String> addressAliases(GroupMember agentMember, UserAgent ua) {
        List<String> out = new ArrayList<>();
        Set<String> seen = ConcurrentHashMap.newKeySet();
        java.util.function.Consumer<String> add = (raw) -> {
            if (!StringUtils.hasText(raw)) return;
            String a = raw.trim();
            if (a.length() < 2) return;
            String key = a.toLowerCase(Locale.ROOT);
            if (seen.add(key)) out.add(a);
        };
        add.accept(ua.getName());
        if (agentMember != null) add.accept(agentMember.getNickname());
        String careerCode = userAgentIdentityService.careerCode(ua);
        String careerName = userAgentIdentityService.careerName(ua);
        add.accept(careerName);
        String persona = safePersona(ua.getId());
        String personaLower = persona != null ? persona.toLowerCase(Locale.ROOT) : "";
        boolean localBridge = LocalBridgeService.isLocalBridge(ua);
        List<String> careerAliases = careerCode != null
                ? CAREER_DESKTOP_ALIASES.getOrDefault(careerCode, List.of())
                : List.of();
        for (String alias : careerAliases) {
            String compact = alias.replace(" ", "");
            boolean inName = ua.getName() != null
                    && ua.getName().toLowerCase(Locale.ROOT).contains(compact.toLowerCase(Locale.ROOT));
            boolean inPersona = personaLower.contains(alias.toLowerCase(Locale.ROOT));
            if (localBridge || inName || inPersona) {
                add.accept(alias);
            }
        }
        if (localBridge) {
            // 多只本机桥并存时，禁止共享「本机」——必须 @Cursor / @WorkBuddy 等专属名
            Long gid = agentMember != null ? agentMember.getGroupId() : null;
            if (countLocalBridgeAgentsInGroup(gid) <= 1) {
                for (String alias : LOCAL_DESKTOP_SHARED_ALIASES) {
                    add.accept(alias);
                }
            }
        } else {
            for (List<String> aliases : CAREER_DESKTOP_ALIASES.values()) {
                for (String alias : aliases) {
                    String compact = alias.replace(" ", "");
                    boolean inName = ua.getName() != null
                            && ua.getName().toLowerCase(Locale.ROOT).contains(compact.toLowerCase(Locale.ROOT));
                    boolean inPersona = personaLower.contains(alias.toLowerCase(Locale.ROOT));
                    if (inName || inPersona) add.accept(alias);
                }
            }
            for (String alias : LOCAL_DESKTOP_SHARED_ALIASES) {
                boolean inName = ua.getName() != null
                        && ua.getName().toLowerCase(Locale.ROOT).contains(alias);
                boolean inPersona = personaLower.contains(alias);
                if (inName || inPersona) add.accept(alias);
            }
        }
        return out;
    }

    private static boolean isVoteYes(String answer) {
        if (!StringUtils.hasText(answer)) return false;
        String t = answer.trim().toUpperCase(Locale.ROOT);
        if (t.contains("YES") || t.startsWith("Y")) return true;
        // 中文模型偶发
        String raw = answer.trim();
        return raw.startsWith("是") || raw.contains("要回") || raw.contains("开口");
    }

    private boolean isMentionedOrReply(GroupMessage userMsg, GroupMember agentMember) {
        UserAgent ua = userAgentRepository.findById(agentMember.getUserAgentId()).orElse(null);
        if (ua == null) return false;
        String name = ua.getName() != null ? ua.getName().trim() : "";
        String content = userMsg.getContent();

        boolean atMe = false;
        if (StringUtils.hasText(content)) {
            for (String alias : addressAliases(agentMember, ua)) {
                if (isAtMentionOf(content, alias)) {
                    atMe = true;
                    break;
                }
            }
        }
        // 仅「人类」引用本 Agent 的消息算点名；Agent 互引要用显式 @
        boolean replyToMe = false;
        if (userMsg.getSenderType() == GroupMessage.SenderType.USER && userMsg.getReplyToId() != null) {
            GroupMessage parent = messageRepository.findById(userMsg.getReplyToId()).orElse(null);
            if (parent != null
                    && parent.getSenderType() == GroupMessage.SenderType.AGENT
                    && agentMember.getUserAgentId().equals(parent.getSenderAgentId())) {
                replyToMe = true;
            }
        }
        // 正文已明确 @ 了别人时，引用回复不算对本 Agent 的点名
        boolean hit = atMe || (replyToMe && !contentHasExplicitAtOfSomeoneElse(content, agentMember, ua));
        if (!hit) return false;
        return canTriggerMention(userMsg, agentMember, ua);
    }

    /** 正文 @ 是否指向「非本 Agent」（对照本 Agent 全部别名）。 */
    private boolean contentHasExplicitAtOfSomeoneElse(String content, GroupMember agentMember, UserAgent ua) {
        if (!StringUtils.hasText(content) || !content.contains("@")) return false;
        for (String alias : addressAliases(agentMember, ua)) {
            if (isAtMentionOf(content, alias)) return false;
        }
        return MENTION.matcher(content).find();
    }

    /**
     * 谁可触发：主人人类始终可；其他人类看 othersMayMention；
     * 其他 Agent 仅同主人（跨主人互 @ 无效）。
     */
    private boolean canTriggerMention(GroupMessage trigger, GroupMember target, UserAgent targetUa) {
        Long ownerId = targetUa.getUserId();
        if (trigger.getSenderType() == GroupMessage.SenderType.USER) {
            Long uid = trigger.getSenderUserId();
            if (ownerId != null && ownerId.equals(uid)) return true;
            return target.isOthersMayMention();
        }
        if (trigger.getSenderType() == GroupMessage.SenderType.AGENT) {
            if (trigger.getSenderAgentId() == null || ownerId == null) return false;
            UserAgent senderUa = userAgentRepository.findById(trigger.getSenderAgentId()).orElse(null);
            return senderUa != null && ownerId.equals(senderUa.getUserId());
        }
        return false;
    }

    /** 正文是否 @ 了本 Agent（全名包含或 @token 为名字前缀，且 token 足够长避免「专家」误伤）。 */
    private static boolean isAtMentionOf(String content, String agentName) {
        String name = agentName.trim();
        if (name.isEmpty()) return false;
        String lower = content.toLowerCase(Locale.ROOT);
        String nameLower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("@" + nameLower)) return true;
        var m = MENTION.matcher(content);
        while (m.find()) {
            String token = m.group(1).trim();
            if (token.isEmpty()) continue;
            String tokenLower = token.toLowerCase(Locale.ROOT);
            if (name.equalsIgnoreCase(token)) return true;
            // token 须至少 2 字，且是名字前缀；禁止用短后缀（如「专家」）匹配「Cursor · 专家」
            if (tokenLower.length() >= 2 && nameLower.startsWith(tokenLower)) return true;
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
            return VoteOutcome.quotaExceeded();
        }
        String raw = userMsg.getContent() == null ? "" : userMsg.getContent().trim();
        String persona = safePersona(ua.getId());
        String aliases = String.join(" / ", addressAliases(agentMember, ua));
        // 启发式观察信号：只喂给模型，不做代码层开口决定
        boolean signalAtMe = isMentionedOrReply(userMsg, agentMember);
        boolean signalSoft = isSoftAddressed(userMsg, agentMember);
        String system = """
                # Role
                你是群聊里的「%s」。现在只做一件事：根据花名册与对话，判断**你自己**本轮要不要说话。
                只输出 YES 或 NO。代码不会替你硬编码决定；下面的 Address signals 只是观察提示。

                # Decision policy
                1. 对方在点名/称呼花名册里的**别人**（哪怕没有 @）→ 你必须 NO，不要抢话、不要代答
                2. 对方在点名/称呼的就是你（全名、花名、本机工具别名如 Cursor、职责称呼，**不必有 @**）→ YES
                3. Address signals 里 explicit_at_or_reply_to_me=yes → 强烈倾向 YES（除非明显误触/广告）
                4. Address signals 里 soft_name_or_alias_in_text=yes → 倾向 YES
                5. 对方让你去问/转告/安排其他同事（例如「你去问问秘书」）→ 仍是对你下指令 → YES，不要因为涉及别人就 NO
                6. 对方在向你抱怨/质问你的行为（例如「你为什么不回」），即使没写名字 → YES
                7. 话题明显落在你的人设/职责（编码、本机、秘书事务等），且没有更合适的在场同事 → YES
                8. 话题更适合花名册里另一位同事，且对方也不是在叫你 → NO
                9. 面向全群的正式同步/点名确认（如「请各位知晓」「让大家知道」「同步给大家」）→ YES，用一两句确认收到即可，不要长篇复读别人已说的内容
                10. 纯闲聊/打招呼：你可以 YES（想接就接），也可以 NO；按人设自然选择
                11. 纯系统旁白（如「已转发到本机」「排队中」）/ 无信息 → NO
                12. 拿不准：若消息是在问「你们 / 大家」且你能答 → YES；否则 NO

                # Output contract
                YES | NO
                """.formatted(ua.getName()).stripIndent();
        String user = """
                # Group
                %s

                # Roster（群里所有人，用他们的真实名字与别名判断点名）
                %s

                # You
                - name: %s
                - also-known-as: %s
                - kind: %s
                - local-bridge: %s
                - persona: %s

                # Address signals（启发式观察，最终由你按 Decision policy 决定）
                - explicit_at_or_reply_to_me: %s
                - soft_name_or_alias_in_text: %s

                # Recent dialogue（时间正序；最后一条是最新消息，可能来自用户或另一位 Agent）
                %s

                # Latest message（请据此投票）
                %s
                """.formatted(
                group.getTitle() != null ? group.getTitle() : "",
                StringUtils.hasText(roster) ? roster : "（暂无）",
                ua.getName(),
                StringUtils.hasText(aliases) ? aliases : ua.getName(),
                ua.getKind() != null ? ua.getKind().name() : "AGENT",
                LocalBridgeService.isLocalBridge(ua)
                        ? ("yes（用户说 "
                        + (userAgentIdentityService.careerName(ua) != null
                                ? userAgentIdentityService.careerName(ua)
                                : "本机工具")
                        + "/本机/connector 常指你）")
                        : "no",
                persona.length() > 500 ? persona.substring(0, 500) : persona,
                signalAtMe ? "yes" : "no",
                signalSoft ? "yes" : "no",
                StringUtils.hasText(dialogue) ? dialogue : "(no prior)",
                raw
        );
        try {
            String answer;
            SystemModel model = pickCheapModel(ua);
            if (model != null) {
                // 投票只要 YES/NO：关掉思考链，避免 max_tokens=8 被 reasoning 吃光
                answer = systemModelChatHelper.chatMessages(
                        model,
                        List.of(Message.system(system), Message.user(user)),
                        0.0,
                        32,
                        true);
            } else {
                ChatResponse resp = llmRouter.chat(ChatRequest.builder()
                        .messages(List.of(Message.system(system), Message.user(user)))
                        .temperature(0.0)
                        .maxTokens(32)
                        .thinking(ChatRequest.Thinking.disabled())
                        .build());
                answer = resp.firstMessage() != null ? resp.firstMessage().content() : "";
            }
            dailyPointsService.recordSpend(ua.getUserId(), ua.getId(), VOTE_POINTS);
            boolean yes = isVoteYes(answer);
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
                    if (LocalBridgeService.isLocalBridge(ua)) {
                        kind = "本机桥同事（Cursor/桌面）";
                    }
                    String digest = safePersona(ua.getId());
                    if (digest.length() > 80) digest = digest.substring(0, 80) + "…";
                    String aka = String.join("/", addressAliases(m, ua));
                    lines.add("- 同事：「" + ua.getName() + "」"
                            + (StringUtils.hasText(aka) ? " 又名「" + aka + "」" : "")
                            + "（" + kind
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
    private String buildReplySystemPrompt(UserAgent ua, ChatGroup group, String persona, String roster) {
        String kindLabel = ua.getKind() == UserAgent.Kind.SECRETARY ? "秘书同事" : "专业同事";
        String personaBlock = StringUtils.hasText(persona) ? persona.trim() : "（未单独配置人设，按可靠、好相处的同事来）";
        String rosterBlock = StringUtils.hasText(roster) ? roster.trim() : "（暂无花名册）";
        String localBlock = "";
        if (LocalBridgeService.isGroupLocalAssistEnabled(ua)) {
            localBlock = "\n" + LocalBridgeService.agentArchitecturePrompt(false) + """

                ## 群聊执行细则
                多数群消息只需云端同事式回复。仅当用户明确要动本机时，在正文最后追加一行：
                   [[LOCAL:给本机应用的可执行任务说明]]
                该行会被平台剥掉，不会原文进群。不确定就不要加，只聊天即可。
                """.stripIndent();
        }
        return """
                # Identity
                你是群「%s」里的真人同事「%s」（%s）。你有自己的性格、专业边界和说话习惯。
                在群里你就是这个人——不是客服话术机，也不是旁白解说员。

                # Group roster（必须知晓，用于判断谁该说话）
                %s

                # Character card（必须遵守）
                %s
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

                # Tools（需要时调用，不要空喊）
                - get_group_info：当前群名/公告/人数等
                - list_group_members / get_group_member：最新花名册与某人资料（成员会变动，不确定就查）
                - recall_memory / store_memory：你自己的长期记忆（单聊+群聊共用）；重要约定要记下来

                # Context usage
                下文是「人类可见」结构化会话：时间、消息号、发送者、引用、@、已读/未读、系统事件。
                把标记为「本轮要回应的最新消息」当作本轮要回应的内容；引用块里有原文，不要说看不见引用。
                """.formatted(
                group.getTitle() != null ? group.getTitle() : "这个群",
                ua.getName(),
                kindLabel,
                rosterBlock,
                personaBlock,
                localBlock
        ).stripIndent();
    }

    /**
     * 语义判定：本轮是否需要客户端/本机参与。任意语言，禁止关键词表。
     * 输出 JSON：{"needLocal":true|false,"instruction":"..."|null}
     */
    private LocalJudgeOutcome judgeLocalDispatch(
            ChatGroup group,
            GroupMessage userMsg,
            UserAgent ua,
            String agentReply,
            String dialogue
    ) {
        try {
            dailyPointsService.assertWithinDailyLimit(ua.getId(), LOCAL_JUDGE_POINTS);
        } catch (Exception e) {
            return LocalJudgeOutcome.skipped();
        }
        String latest = userMsg.getContent() == null ? "" : userMsg.getContent().trim();
        String system = """
                You gatekeep the local-desktop Connector (Cursor / Workbuddy / Windsurf / Codex).
                Default is needLocal=false. Only set needLocal=true when the LATEST user intent
                clearly requires the local machine itself (the agent linked to this desktop app).

                needLocal=true ONLY if the user needs at least one of:
                - read / write / edit / create / delete files or code on the local disk
                - run shell / build / test / install commands on the local machine
                - inspect local environment (local paths, local git, IDE, Connector, installed tools on PC)
                - explicitly asks the local app/agent to execute a task on their computer

                needLocal=false for ALL of the following (even if the speaker is a local-bridge agent):
                - pure chat, greetings, opinions, explanations, brainstorming
                - syncing / announcing personal info or group roster ("tell everyone", "让他们知道")
                - group coordination, reminding members, "who hasn't spoken"
                - server-side / cloud DB / group settings questions that do not need the PC filesystem
                - the agent merely planning or promising to look later, without a concrete local action now
                - repeating or confirming chat content

                When unsure → needLocal=false.
                Judge by meaning/intent in ANY language — never by keyword lists.

                Output ONLY compact JSON, no markdown:
                {"needLocal":true,"instruction":"<one executable task for the local app>"}
                or
                {"needLocal":false,"instruction":null}

                When needLocal=true, instruction must be concrete and actionable; prefer the user's language.
                """.stripIndent();
        String user = """
                # Group
                %s

                # Agent
                %s

                # Recent dialogue
                %s

                # Latest user message
                %s

                # Agent's chat reply just now (context only; may be incomplete)
                %s
                """.formatted(
                group.getTitle() != null ? group.getTitle() : "",
                ua.getName(),
                StringUtils.hasText(dialogue) ? dialogue : "(none)",
                latest,
                StringUtils.hasText(agentReply) ? clip(agentReply, 800) : "(empty)"
        );
        try {
            String answer;
            SystemModel model = pickCheapModel(ua);
            if (model != null) {
                answer = systemModelChatHelper.chatMessages(
                        model,
                        List.of(Message.system(system), Message.user(user)),
                        0.0,
                        256,
                        true);
            } else {
                ChatResponse resp = llmRouter.chat(ChatRequest.builder()
                        .messages(List.of(Message.system(system), Message.user(user)))
                        .temperature(0.0)
                        .maxTokens(256)
                        .thinking(ChatRequest.Thinking.disabled())
                        .build());
                answer = resp.firstMessage() != null ? resp.firstMessage().content() : "";
            }
            dailyPointsService.recordSpend(ua.getUserId(), ua.getId(), LOCAL_JUDGE_POINTS);
            LocalJudgeOutcome parsed = parseLocalJudgeJson(answer);
            log.info("[GroupAgent] local-judge agent={} need={} instr={}",
                    ua.getName(),
                    parsed.needLocal(),
                    clip(parsed.instruction() != null ? parsed.instruction() : "", 60));
            return parsed;
        } catch (Exception e) {
            log.warn("[GroupAgent] local-judge failed agent={}: {}", ua.getId(), e.getMessage());
            return LocalJudgeOutcome.skipped();
        }
    }

    private LocalJudgeOutcome parseLocalJudgeJson(String raw) {
        if (!StringUtils.hasText(raw)) return LocalJudgeOutcome.no(LOCAL_JUDGE_POINTS);
        String t = raw.trim();
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end <= start) return LocalJudgeOutcome.no(LOCAL_JUDGE_POINTS);
        try {
            JsonNode node = objectMapper.readTree(t.substring(start, end + 1));
            boolean need = node.path("needLocal").asBoolean(false)
                    || "true".equalsIgnoreCase(node.path("needLocal").asText(""));
            String instruction = null;
            JsonNode instr = node.get("instruction");
            if (instr != null && !instr.isNull()) {
                instruction = instr.asText(null);
            }
            if (need && StringUtils.hasText(instruction)) {
                return LocalJudgeOutcome.yes(instruction.trim(), LOCAL_JUDGE_POINTS);
            }
            return LocalJudgeOutcome.no(LOCAL_JUDGE_POINTS);
        } catch (Exception e) {
            log.debug("[GroupAgent] local-judge JSON parse fail: {}", e.getMessage());
            return LocalJudgeOutcome.no(LOCAL_JUDGE_POINTS);
        }
    }

    /** 从云端回复中拆出 [[LOCAL:...]] 本机任务说明 */
    private record LocalDispatchParsed(String chatText, String localInstruction) {
        static LocalDispatchParsed parse(String raw) {
            if (!StringUtils.hasText(raw)) return new LocalDispatchParsed("", null);
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "\\[\\[LOCAL:\\s*([\\s\\S]+?)\\]\\]\\s*$",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(raw.trim());
            if (!m.find()) return new LocalDispatchParsed(raw.trim(), null);
            String instruction = m.group(1) != null ? m.group(1).trim() : null;
            String chat = raw.substring(0, m.start()).trim();
            if (!StringUtils.hasText(instruction)) {
                return new LocalDispatchParsed(StringUtils.hasText(chat) ? chat : raw.trim(), null);
            }
            return new LocalDispatchParsed(chat, instruction);
        }
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
        List<Message> msgs = groupAgentContextService.buildLlmMessages(
                group, ua, buildReplySystemPrompt(ua, group, persona, roster), userMsg);
        String soul = groupMemoryCaptureService.soulPromptBlock(
                ua.getUserId(), ua.getId(), userMsg.getContent());
        if (StringUtils.hasText(soul) && !msgs.isEmpty()) {
            msgs.add(1, Message.system(soul));
        }

        try {
            SystemModel model = pickCheapModel(ua);
            // 可调 get_group_info / list_group_members / 记忆工具，再回群聊短正文
            String content = groupAgentToolService.chatWithGroupTools(model, msgs, group, ua);
            // 只要完成了一次模型调用，就算分析消耗（无论是否空回复）
            dailyPointsService.recordSpend(ua.getUserId(), ua.getId(), GENERATE_POINTS);
            if (!StringUtils.hasText(content)) {
                log.warn("[GroupAgent] generate empty content agent={} group={}",
                        ua.getName(), group.getPublicId());
                content = "在，刚才没组织好词，你再跟我说一遍？";
            }
            groupMemoryCaptureService.captureAfterGroupTurn(
                    ua.getId(), group.getId(), userMsg.getId(), content);
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
