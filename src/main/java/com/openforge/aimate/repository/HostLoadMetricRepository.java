package com.openforge.aimate.repository;

import com.openforge.aimate.domain.HostLoadMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HostLoadMetricRepository extends JpaRepository<HostLoadMetric, Long> {

    List<HostLoadMetric> findByHostNameAndCreateTimeBetweenOrderByCreateTimeAsc(
            String hostName,
            LocalDateTime from,
            LocalDateTime to
    );

    List<HostLoadMetric> findByCreateTimeBetweenOrderByCreateTimeAsc(
            LocalDateTime from,
            LocalDateTime to
    );
}

