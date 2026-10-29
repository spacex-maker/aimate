package com.openforge.aimate.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期把租期已过的雇佣 Agent 标为 EXPIRED。
 * 群聊/本机桥不应依赖用户打开「我的 Agent」才触发懒刷新。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentExpirySweepJob {

    private final UserAgentService userAgentService;

    @Scheduled(fixedDelayString = "${aimate.agent.expiry-sweep-ms:300000}", initialDelay = 60_000)
    public void sweep() {
        try {
            int n = userAgentService.sweepExpiredAgents();
            if (n > 0) {
                log.info("[AgentExpiry] marked {} agent(s) EXPIRED", n);
            }
        } catch (Exception e) {
            log.warn("[AgentExpiry] sweep failed: {}", e.toString());
        }
    }
}