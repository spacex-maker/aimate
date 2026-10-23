package com.openforge.aimate.repository;

import com.openforge.aimate.domain.UserAgentKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAgentKnowledgeRepository extends JpaRepository<UserAgentKnowledge, Long> {
    List<UserAgentKnowledge> findByUserAgentIdOrderByIdAsc(Long userAgentId);

    void deleteByUserAgentId(Long userAgentId);
}
