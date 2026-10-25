package com.openforge.aimate.localbridge;

import com.openforge.aimate.config.LocalBridgeProperties;
import com.openforge.aimate.domain.LocalBridgeTask;
import com.openforge.aimate.repository.LocalBridgeTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 回收租约过期的 CLAIMED 任务 → PENDING，避免 Worker 崩溃后任务永久卡死。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalBridgeLeaseReclaimJob {

    private final LocalBridgeTaskRepository taskRepository;
    private final LocalBridgeProperties properties;

    @Scheduled(fixedDelayString = "${aimate.local-bridge.lease-reclaim-interval-ms:60000}", initialDelay = 45_000)
    @Transactional
    public void reclaim() {
        int n = taskRepository.reclaimExpiredLeases(
                LocalDateTime.now(),
                LocalBridgeTask.Status.PENDING,
                LocalBridgeTask.Status.CLAIMED);
        if (n > 0) {
            log.info("[LocalBridge] reclaimed {} expired CLAIMED task(s); lease={}s",
                    n, properties.getClaimLeaseSeconds());
        }
    }
}
