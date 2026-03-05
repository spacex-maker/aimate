package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.HostResourceStatusDto;
import com.openforge.aimate.domain.HostLoadMetric;
import com.openforge.aimate.repository.HostLoadMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HostLoadMetricService {

    private final HostLoadMetricRepository repository;

    public void record(String hostName, HostResourceStatusDto status) {
        if (status == null || hostName == null || hostName.isBlank()) return;
        HostLoadMetric m = new HostLoadMetric();
        m.setHostName(hostName);
        m.setCpuLoadPercent(status.systemCpuLoadPercent());
        m.setMemAvailablePercent(status.hostAvailableMemoryPercent());
        m.setRootFsUsedPercent(status.rootFsUsedPercent());
        repository.save(m);
    }

    public List<HostLoadMetric> list(String hostName, LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) return List.of();
        if (hostName == null || hostName.isBlank()) {
            return repository.findByCreateTimeBetweenOrderByCreateTimeAsc(from, to);
        }
        return repository.findByHostNameAndCreateTimeBetweenOrderByCreateTimeAsc(hostName, from, to);
    }
}

