package com.openforge.aimate.systemagent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemAgentScheduler {

    private final SystemAgentService systemAgentService;

    /** 每分钟检查一次是否到达系统 Agent 的每日固定维护时间。 */
    @Scheduled(fixedDelayString = "${aimate.system-agent.tick-ms:60000}", initialDelay = 45_000)
    public void tick() {
        try {
            systemAgentService.tickSchedules();
        } catch (Exception e) {
            log.warn("[SystemAgentScheduler] tick failed: {}", e.getMessage());
        }
    }
}
