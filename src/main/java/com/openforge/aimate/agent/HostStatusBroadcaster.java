package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.HostResourceStatusDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通过 WebSocket 定期推送宿主机资源状态到 /topic/admin/host-status，
 * 供管理后台容器监控页实时展示（约每秒一次）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HostStatusBroadcaster {

    private static final String DESTINATION = "/topic/admin/host-status";

    private final HostStatusService hostStatusService;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedRate = 1000L)
    public void broadcast() {
        try {
            HostResourceStatusDto dto = hostStatusService.snapshot();
            messagingTemplate.convertAndSend(DESTINATION, dto);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("[HostStatus] broadcast failed: {}", e.getMessage());
            }
        }
    }
}

