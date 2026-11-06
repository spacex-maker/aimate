package com.openforge.aimate.repository;

import com.openforge.aimate.domain.SystemProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemProviderRepository extends JpaRepository<SystemProvider, Long> {
    Optional<SystemProvider> findByCodeIgnoreCase(String code);
    List<SystemProvider> findAllByOrderBySortOrderAscIdAsc();
    boolean existsByCodeIgnoreCase(String code);
}