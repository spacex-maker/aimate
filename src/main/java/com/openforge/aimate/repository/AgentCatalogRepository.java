package com.openforge.aimate.repository;

import com.openforge.aimate.domain.AgentCatalog;
import com.openforge.aimate.domain.AgentTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentCatalogRepository extends JpaRepository<AgentCatalog, Long> {
    List<AgentCatalog> findByEnabledTrueOrderBySortOrderAscIdAsc();
    List<AgentCatalog> findAllByOrderBySortOrderAscIdAsc();
    List<AgentCatalog> findByCareerIdOrderBySortOrderAscIdAsc(Long careerId);
    Optional<AgentCatalog> findByCareerIdAndTier(Long careerId, AgentTier tier);
}
