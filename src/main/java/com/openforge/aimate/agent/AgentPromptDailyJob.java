package com.openforge.aimate.agent;

import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.repository.UserAgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPromptDailyJob {

    private final UserAgentRepository userAgentRepository;
    private final AgentPromptService agentPromptService;

    /** 每日 04:20 优化活跃秘书提示词 */
    @Scheduled(cron = "0 20 4 * * *")
    public void optimizeSecretaries() {
        List<UserAgent> list = userAgentRepository.findByStatus(UserAgent.Status.ACTIVE).stream()
                .filter(a -> a.getKind() == UserAgent.Kind.SECRETARY)
                .toList();
        log.info("[AgentPromptDaily] optimizing {} secretaries", list.size());
        for (UserAgent ua : list) {
            try {
                agentPromptService.dailyOptimize(ua);
            } catch (Exception e) {
                log.warn("[AgentPromptDaily] agent {} failed: {}", ua.getId(), e.toString());
            }
        }
    }
}
