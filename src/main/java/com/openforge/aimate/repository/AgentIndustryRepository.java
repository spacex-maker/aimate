package com.openforge.aimate.repository;

import com.openforge.aimate.domain.AgentIndustry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentIndustryRepository extends JpaRepository<AgentIndustry, Long> {
    Optional<AgentIndustry> findByCodeIgnoreCase(String code);
    List<AgentIndustry> findByEnabledTrueOrderBySortOrderAscIdAsc();
    List<AgentIndustry> findAllByOrderBySortOrderAscIdAsc();
}
