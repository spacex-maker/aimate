package com.openforge.aimate.repository;

import com.openforge.aimate.domain.LocalBridgeTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocalBridgeTaskRepository extends JpaRepository<LocalBridgeTask, Long> {

    Optional<LocalBridgeTask> findByPublicId(String publicId);

    Optional<LocalBridgeTask> findFirstByUserAgentIdAndStatusOrderByIdAsc(
            Long userAgentId, LocalBridgeTask.Status status);

    boolean existsByUserAgentIdAndTriggerMessageIdAndStatusIn(
            Long userAgentId, Long triggerMessageId, List<LocalBridgeTask.Status> statuses);

    Optional<LocalBridgeTask> findByPublicIdAndUserAgentId(String publicId, Long userAgentId);

    Page<LocalBridgeTask> findByGroupIdAndUserAgentIdOrderByIdDesc(
            Long groupId, Long userAgentId, Pageable pageable);

    Page<LocalBridgeTask> findByGroupIdAndUserAgentIdAndStatusOrderByIdDesc(
            Long groupId, Long userAgentId, LocalBridgeTask.Status status, Pageable pageable);

    long countByGroupIdAndUserAgentIdAndStatus(Long groupId, Long userAgentId, LocalBridgeTask.Status status);

    @Query("""
            SELECT t.status, COUNT(t) FROM LocalBridgeTask t
            WHERE t.groupId = :groupId AND t.userAgentId = :userAgentId
            GROUP BY t.status
            """)
    List<Object[]> countByStatusGrouped(
            @Param("groupId") Long groupId,
            @Param("userAgentId") Long userAgentId
    );
}
