package com.openforge.aimate.repository;

import com.openforge.aimate.domain.GroupAgentContext;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupAgentContextRepository extends JpaRepository<GroupAgentContext, Long> {

    Optional<GroupAgentContext> findByGroupIdAndUserAgentId(Long groupId, Long userAgentId);

    List<GroupAgentContext> findByGroupId(Long groupId);
}
