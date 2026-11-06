package com.openforge.aimate.repository;

import com.openforge.aimate.domain.SystemAgent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SystemAgentRepository extends JpaRepository<SystemAgent, Long> {

    Optional<SystemAgent> findByCodeIgnoreCase(String code);

    List<SystemAgent> findByEnabledTrueAndType(String type);

    @Query("""
            SELECT a FROM SystemAgent a
            WHERE (:q IS NULL OR :q = ''
               OR LOWER(a.code) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(a.description, '')) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:type IS NULL OR :type = '' OR a.type = :type)
              AND (:enabled IS NULL OR a.enabled = :enabled)
            ORDER BY a.id DESC
            """)
    List<SystemAgent> search(
            @Param("q") String q,
            @Param("type") String type,
            @Param("enabled") Boolean enabled
    );
}
