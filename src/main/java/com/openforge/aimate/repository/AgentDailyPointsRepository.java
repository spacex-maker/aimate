package com.openforge.aimate.repository;

import com.openforge.aimate.domain.AgentDailyPoints;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface AgentDailyPointsRepository extends JpaRepository<AgentDailyPoints, Long> {
    Optional<AgentDailyPoints> findByUserAgentIdAndDay(Long userAgentId, LocalDate day);
}
