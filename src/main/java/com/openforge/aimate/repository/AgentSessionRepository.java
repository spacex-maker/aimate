package com.openforge.aimate.repository;

import com.openforge.aimate.domain.AgentSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentSessionRepository extends JpaRepository<AgentSession, Long> {

    Optional<AgentSession> findBySessionId(String sessionId);

    List<AgentSession> findByStatus(AgentSession.SessionStatus status);
}
