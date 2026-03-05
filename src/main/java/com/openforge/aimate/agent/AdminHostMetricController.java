package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.HostLoadMetricDto;
import com.openforge.aimate.domain.HostLoadMetric;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 管理员查询宿主机负载历史数据（用于图表）。
 * 路径受 SecurityConfig 中 /api/admin/** + ADMIN 权限保护。
 */
@RestController
@RequestMapping("/api/admin/host-metrics")
@RequiredArgsConstructor
public class AdminHostMetricController {

    private final HostLoadMetricService hostLoadMetricService;

    @GetMapping
    public ResponseEntity<List<HostLoadMetricDto>> list(
            @RequestParam(name = "hostName", required = false) String hostName,
            @RequestParam(name = "from", required = false) Long fromEpochMillis,
            @RequestParam(name = "to", required = false) Long toEpochMillis
    ) {
        Instant now = Instant.now();
        Instant toInstant = toEpochMillis != null ? Instant.ofEpochMilli(toEpochMillis) : now;
        // 默认最近 7 天
        Instant fromInstant = fromEpochMillis != null ? Instant.ofEpochMilli(fromEpochMillis) : toInstant.minusSeconds(7L * 24 * 3600);
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime from = LocalDateTime.ofInstant(fromInstant, zone);
        LocalDateTime to = LocalDateTime.ofInstant(toInstant, zone);

        List<HostLoadMetric> list = hostLoadMetricService.list(hostName, from, to);
        List<HostLoadMetricDto> dtoList = list.stream()
                .map(HostLoadMetricDto::from)
                .toList();
        return ResponseEntity.ok(dtoList);
    }
}

