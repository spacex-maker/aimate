package com.openforge.aimate.office;

import com.openforge.aimate.agent.TavilySearchService;
import com.openforge.aimate.billing.InsufficientPointsException;
import com.openforge.aimate.billing.PointsService;
import com.openforge.aimate.config.SystemConfigService;
import com.openforge.aimate.domain.AgentCatalog;
import com.openforge.aimate.domain.PointLedger;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.domain.UserOffice;
import com.openforge.aimate.llm.LlmRouter;
import com.openforge.aimate.llm.model.ChatRequest;
import com.openforge.aimate.llm.model.ChatResponse;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.memory.SoulMemoryBridge;
import com.openforge.aimate.repository.AgentCareerRepository;
import com.openforge.aimate.repository.AgentCatalogRepository;
import com.openforge.aimate.repository.AgentIndustryRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import com.openforge.aimate.repository.UserOfficeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 有办公室的用户：闲时按 Agent 人设联网学习并写入 Soul（受日积分上限约束）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfficeIdleAutonomyJob {

    private final UserOfficeRepository officeRepository;
    private final UserAgentRepository userAgentRepository;
    private final OfficeCvmProperties props;
    private final AgentDailyPointsService dailyPointsService;
    private final PointsService pointsService;
    private final SoulMemoryBridge soulMemoryBridge;
    private final TavilySearchService tavilySearchService;
    private final SystemConfigService systemConfigService;
    private final LlmRouter llmRouter;
    private final AgentCatalogRepository catalogRepository;
    private final AgentCareerRepository careerRepository;
    private final AgentIndustryRepository industryRepository;
    private final OfficeRemoteExecutor officeRemoteExecutor;

    /** agentId → last tick */
    private final ConcurrentHashMap<Long, LocalDateTime> lastTickByAgent = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${aimate.office.idle-scan-ms:1800000}", initialDelayString = "120000")
    public void tick() {
        List<UserOffice> offices = officeRepository.findByStatus(UserOffice.Status.ACTIVE);
        for (UserOffice office : offices) {
            if (office.getExpiresAt() != null && office.getExpiresAt().isBefore(LocalDateTime.now())) {
                continue;
            }
            List<UserAgent> agents = userAgentRepository.findByUserIdOrderByUpdateTimeDesc(office.getUserId());
            for (UserAgent ua : agents) {
                if (ua.getStatus() != UserAgent.Status.ACTIVE) continue;
                try {
                    maybeLearn(office, ua);
                } catch (InsufficientPointsException e) {
                    log.debug("[IdleAutonomy] skip agent={}: {}", ua.getId(), e.getMessage());
                } catch (Exception e) {
                    log.warn("[IdleAutonomy] agent={} failed: {}", ua.getId(), e.getMessage());
                }
            }
            office.setLastIdleTickAt(LocalDateTime.now());
            officeRepository.save(office);
        }
    }

    private void maybeLearn(UserOffice office, UserAgent ua) {
        int hours = Math.max(1, props.getIdleTickHours());
        LocalDateTime last = lastTickByAgent.get(ua.getId());
        if (last != null && last.isAfter(LocalDateTime.now().minusHours(hours))) {
            return;
        }
        if (ua.getDailyPointsLimit() != null && ua.getDailyPointsLimit() > 0) {
            long spent = dailyPointsService.spentToday(ua.getId());
            if (spent >= ua.getDailyPointsLimit()) return;
        }

        Optional<String> apiKey = systemConfigService.getTavilyApiKey();
        if (apiKey.isEmpty()) return;

        String persona = buildPersona(ua);
        String query = "最新行业动态 学习要点 " + persona;
        String searchOut = tavilySearchService.search(apiKey.get(), query, 5, "basic", "general");
        if (searchOut != null && searchOut.startsWith("[ToolError]")) {
            log.debug("[IdleAutonomy] search failed: {}", searchOut);
            return;
        }

        String prompt = """
                你是一名%s。请根据以下检索结果，提炼 3 条对你职业有用的知识要点（简洁中文），
                用于丰富长期记忆。不要废话。
                
                检索结果：
                %s
                """.formatted(persona, searchOut == null ? "" : searchOut.substring(0, Math.min(4000, searchOut.length())));

        ChatResponse resp = llmRouter.chat(ChatRequest.simple(null, List.of(Message.user(prompt))));
        String content = extractContent(resp);
        if (!StringUtils.hasText(content)) return;

        long charge = 50;
        pointsService.deduct(
                ua.getUserId(), charge, PointLedger.IDLE_AUTONOMY, "IDLE",
                String.valueOf(ua.getId()), null, null,
                "闲时自主学习 · " + ua.getName(), ua.getId()
        );

        String trimmed = content.trim();
        if (trimmed.length() > 800) trimmed = trimmed.substring(0, 800);
        soulMemoryBridge.store(trimmed, "FACT", 0.55f, ua.getUserId(), ua.getId());

        try {
            officeRemoteExecutor.write(
                    office,
                    "/home/ubuntu/aimate-learning/" + ua.getId() + "-" + System.currentTimeMillis() + ".md",
                    "# Idle learning\n\n" + trimmed
            );
        } catch (Exception ignored) {
        }
        lastTickByAgent.put(ua.getId(), LocalDateTime.now());
        log.info("[IdleAutonomy] learned for agent={} user={}", ua.getId(), ua.getUserId());
    }

    private static String extractContent(ChatResponse resp) {
        if (resp == null || resp.choices() == null || resp.choices().isEmpty()) return null;
        var msg = resp.choices().get(0).message();
        return msg != null ? msg.content() : null;
    }

    private String buildPersona(UserAgent ua) {
        StringBuilder sb = new StringBuilder();
        sb.append(ua.getName() != null ? ua.getName() : "Agent");
        if (StringUtils.hasText(ua.getPersonality())) {
            sb.append("，性格：").append(ua.getPersonality());
        }
        if (ua.getCatalogId() != null) {
            Optional<AgentCatalog> cat = catalogRepository.findById(ua.getCatalogId());
            cat.ifPresent(c -> careerRepository.findById(c.getCareerId()).ifPresent(career -> {
                sb.append("，职业：").append(career.getName());
                if (career.getIndustryId() != null) {
                    industryRepository.findById(career.getIndustryId()).ifPresent(ind ->
                            sb.append("，行业：").append(ind.getName()));
                }
            }));
        }
        return sb.toString();
    }
}
