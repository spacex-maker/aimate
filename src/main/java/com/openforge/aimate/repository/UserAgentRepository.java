package com.openforge.aimate.repository;

import com.openforge.aimate.domain.UserAgent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAgentRepository extends JpaRepository<UserAgent, Long> {
    List<UserAgent> findByUserIdOrderByUpdateTimeDesc(Long userId);
    Optional<UserAgent> findByIdAndUserId(Long id, Long userId);
    Optional<UserAgent> findByUserIdAndKind(Long userId, UserAgent.Kind kind);
    Optional<UserAgent> findByUserIdAndExternalAgentKey(Long userId, String externalAgentKey);
    List<UserAgent> findByStatus(UserAgent.Status status);
}
