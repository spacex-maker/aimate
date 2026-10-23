package com.openforge.aimate.repository;

import com.openforge.aimate.domain.OfficePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfficePlanRepository extends JpaRepository<OfficePlan, Long> {
    Optional<OfficePlan> findByCodeIgnoreCase(String code);
    List<OfficePlan> findByEnabledTrueOrderBySortOrderAscIdAsc();
    List<OfficePlan> findAllByOrderBySortOrderAscIdAsc();
}
