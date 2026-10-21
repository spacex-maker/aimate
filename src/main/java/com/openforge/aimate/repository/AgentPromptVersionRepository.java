package com.openforge.aimate.repository;

import com.openforge.aimate.domain.AgentPromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentPromptVersionRepository extends JpaRepository<AgentPromptVersion, Long> {
    List<AgentPromptVersion> findByUserAgentIdOrderByVersionNoDesc(Long userAgentId);
    Optional<AgentPromptVersion> findByUserAgentIdAndVersionNo(Long userAgentId, int versionNo);
    Optional<AgentPromptVersion> findFirstByUserAgentIdAndActiveTrue(Long userAgentId);
    Optional<AgentPromptVersion> findByIdAndUserAgentId(Long id, Long userAgentId);
}
