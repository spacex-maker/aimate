package com.openforge.aimate.repository;

import com.openforge.aimate.domain.AgentTier;
import com.openforge.aimate.domain.AgentTierModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentTierModelRepository extends JpaRepository<AgentTierModel, Long> {
    List<AgentTierModel> findByTierOrderBySortOrderAscIdAsc(AgentTier tier);
    List<AgentTierModel> findAllByOrderByTierAscSortOrderAscIdAsc();
    Optional<AgentTierModel> findFirstByTierAndDefaultModelTrue(AgentTier tier);
    void deleteByTier(AgentTier tier);
}
