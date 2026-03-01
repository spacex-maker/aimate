package com.openforge.aimate.repository;

import com.openforge.aimate.domain.AssistantMessageVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssistantMessageVersionRepository extends JpaRepository<AssistantMessageVersion, Long> {

    List<AssistantMessageVersion> findByAgentSessionMessage_IdOrderByVersionDesc(Long agentSessionMessageId);
}
