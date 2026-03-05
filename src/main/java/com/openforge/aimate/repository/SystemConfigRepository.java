package com.openforge.aimate.repository;

import com.openforge.aimate.domain.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    Optional<SystemConfig> findByConfigKey(String configKey);

    List<SystemConfig> findAllByOrderByConfigKeyAsc();
}
