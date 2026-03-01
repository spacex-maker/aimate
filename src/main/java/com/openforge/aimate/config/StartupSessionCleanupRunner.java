package com.openforge.aimate.config;

import com.openforge.aimate.repository.AgentSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用启动时：1) 将 ACTIVE/PAUSED 置为 IDLE（进程已退出）；2) 将旧状态 PENDING/COMPLETED/FAILED 迁移为 IDLE。
 */
@Slf4j
@Component
@Order(Integer.MIN_VALUE)
@RequiredArgsConstructor
public class StartupSessionCleanupRunner implements ApplicationRunner {

    private static final String RESTART_MSG = "Session interrupted by server restart.";

    private final AgentSessionRepository sessionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int a = sessionRepository.markActiveOrPausedAsIdle(RESTART_MSG);
        int b = sessionRepository.migrateLegacyStatusToIdle();
        if (a > 0) {
            log.info("[Startup] Marked {} session(s) as IDLE (were ACTIVE/PAUSED before restart).", a);
        }
        if (b > 0) {
            log.debug("[Startup] Migrated {} session(s) from legacy status to IDLE.", b);
        }
    }
}
