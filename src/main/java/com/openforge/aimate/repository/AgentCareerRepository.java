package com.openforge.aimate.repository;

import com.openforge.aimate.domain.AgentCareer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentCareerRepository extends JpaRepository<AgentCareer, Long> {
    Optional<AgentCareer> findByCodeIgnoreCase(String code);
    List<AgentCareer> findByEnabledTrueOrderBySortOrderAscIdAsc();
    List<AgentCareer> findAllByOrderBySortOrderAscIdAsc();
    List<AgentCareer> findByIndustryIdAndEnabledTrueOrderBySortOrderAscIdAsc(Long industryId);
    List<AgentCareer> findByIndustryIdIsNullAndEnabledTrueOrderBySortOrderAscIdAsc();
}
