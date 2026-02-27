package com.openforge.aimate.repository;

import com.openforge.aimate.domain.AgentTool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentToolRepository extends JpaRepository<AgentTool, Long> {

    Optional<AgentTool> findByToolName(String toolName);

    /** Load all active tools to build the LLM tools array at session start. */
    List<AgentTool> findByIsActiveTrue();
}
