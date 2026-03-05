package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.HostResourceStatusDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * 周期性将宿主机负载快照写入数据库 host_load_metric 表，默认每分钟一条。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HostLoadMetricRecorder {

    private final HostStatusService hostStatusService;
    private final HostLoadMetricService hostLoadMetricService;

    @Scheduled(fixedRate = 60_000L)
    public void record() {
        try {
            HostResourceStatusDto status = hostStatusService.snapshot();
            String hostName = resolveHostName();
            hostLoadMetricService.record(hostName, status);
        } catch (Exception e) {
            log.warn("[HostLoadMetric] record failed: {}", e.getMessage());
        }
    }

    private String resolveHostName() {
        try {
            String env = System.getenv("HOSTNAME");
            if (env != null && !env.isBlank()) return env;
            env = System.getenv("COMPUTERNAME");
            if (env != null && !env.isBlank()) return env;
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }
}

