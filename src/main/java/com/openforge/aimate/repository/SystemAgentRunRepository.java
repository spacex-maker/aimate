package com.openforge.aimate.repository;

import com.openforge.aimate.domain.SystemAgentRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemAgentRunRepository extends JpaRepository<SystemAgentRun, Long> {
    Page<SystemAgentRun> findByAgentIdOrderByIdDesc(Long agentId, Pageable pageable);
}
