package com.openforge.aimate.repository;

import com.openforge.aimate.domain.LocalBridgeTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LocalBridgeTaskRepository extends JpaRepository<LocalBridgeTask, Long> {

    Optional<LocalBridgeTask> findByPublicId(String publicId);

    Optional<LocalBridgeTask> findFirstByUserAgentIdAndStatusOrderByIdAsc(
            Long userAgentId, LocalBridgeTask.Status status);

    /** 优先领最新一单，避免旧「在吗」堵死新任务 */
    Optional<LocalBridgeTask> findFirstByUserAgentIdAndStatusOrderByIdDesc(
            Long userAgentId, LocalBridgeTask.Status status);

    List<LocalBridgeTask> findByUserAgentIdAndStatusOrderByIdDesc(
            Long userAgentId, LocalBridgeTask.Status status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE LocalBridgeTask t
            SET t.status = :cancelled,
                t.completedAt = :now,
                t.errorMessage = :reason
            WHERE t.userAgentId = :userAgentId
              AND t.status = :pending
              AND t.groupId = :groupId
              AND (:keepId IS NULL OR t.id <> :keepId)
            """)
    int cancelPendingInGroup(
            @Param("userAgentId") Long userAgentId,
            @Param("groupId") Long groupId,
            @Param("keepId") Long keepId,
            @Param("pending") LocalBridgeTask.Status pending,
            @Param("cancelled") LocalBridgeTask.Status cancelled,
            @Param("now") LocalDateTime now,
            @Param("reason") String reason
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE LocalBridgeTask t
            SET t.status = :cancelled,
                t.completedAt = :now,
                t.errorMessage = :reason
            WHERE t.userAgentId = :userAgentId
              AND t.status = :pending
              AND t.sessionId = :sessionId
              AND (:keepId IS NULL OR t.id <> :keepId)
            """)
    int cancelPendingInSession(
            @Param("userAgentId") Long userAgentId,
            @Param("sessionId") String sessionId,
            @Param("keepId") Long keepId,
            @Param("pending") LocalBridgeTask.Status pending,
            @Param("cancelled") LocalBridgeTask.Status cancelled,
            @Param("now") LocalDateTime now,
            @Param("reason") String reason
    );

    /** 同群进行中任务（PENDING/CLAIMED）全部标记 superseded 并 CANCELLED */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE LocalBridgeTask t
            SET t.status = :cancelled,
                t.superseded = TRUE,
                t.completedAt = :now,
                t.leaseUntil = NULL,
                t.errorMessage = :reason
            WHERE t.userAgentId = :userAgentId
              AND t.groupId = :groupId
              AND t.status IN (:pending, :claimed)
              AND (:keepId IS NULL OR t.id <> :keepId)
            """)
    int supersedeActiveInGroup(
            @Param("userAgentId") Long userAgentId,
            @Param("groupId") Long groupId,
            @Param("keepId") Long keepId,
            @Param("pending") LocalBridgeTask.Status pending,
            @Param("claimed") LocalBridgeTask.Status claimed,
            @Param("cancelled") LocalBridgeTask.Status cancelled,
            @Param("now") LocalDateTime now,
            @Param("reason") String reason
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE LocalBridgeTask t
            SET t.status = :cancelled,
                t.superseded = TRUE,
                t.completedAt = :now,
                t.leaseUntil = NULL,
                t.errorMessage = :reason
            WHERE t.userAgentId = :userAgentId
              AND t.sessionId = :sessionId
              AND t.status IN (:pending, :claimed)
              AND (:keepId IS NULL OR t.id <> :keepId)
            """)
    int supersedeActiveInSession(
            @Param("userAgentId") Long userAgentId,
            @Param("sessionId") String sessionId,
            @Param("keepId") Long keepId,
            @Param("pending") LocalBridgeTask.Status pending,
            @Param("claimed") LocalBridgeTask.Status claimed,
            @Param("cancelled") LocalBridgeTask.Status cancelled,
            @Param("now") LocalDateTime now,
            @Param("reason") String reason
    );

    @Query("""
            SELECT COALESCE(MAX(t.intentEpoch), 0) FROM LocalBridgeTask t
            WHERE t.userAgentId = :userAgentId AND t.groupId = :groupId
            """)
    Long maxIntentEpochInGroup(
            @Param("userAgentId") Long userAgentId,
            @Param("groupId") Long groupId
    );

    @Query("""
            SELECT COALESCE(MAX(t.intentEpoch), 0) FROM LocalBridgeTask t
            WHERE t.userAgentId = :userAgentId AND t.sessionId = :sessionId
            """)
    Long maxIntentEpochInSession(
            @Param("userAgentId") Long userAgentId,
            @Param("sessionId") String sessionId
    );

    boolean existsByUserAgentIdAndTriggerMessageIdAndStatusIn(
            Long userAgentId, Long triggerMessageId, List<LocalBridgeTask.Status> statuses);

    boolean existsByUserAgentIdAndSessionIdAndStatusIn(
            Long userAgentId, String sessionId, List<LocalBridgeTask.Status> statuses);

    Optional<LocalBridgeTask> findByUserAgentIdAndIdempotencyKey(Long userAgentId, String idempotencyKey);

    long countByUserAgentIdAndGroupIdAndTriggerMessageIdGreaterThanAndStatusNot(
            Long userAgentId, Long groupId, Long triggerMessageId, LocalBridgeTask.Status status);

    long countByUserAgentIdAndSessionIdAndTriggerMessageIdGreaterThanAndStatusNot(
            Long userAgentId, String sessionId, Long triggerMessageId, LocalBridgeTask.Status status);

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

    /** 条件领取：仅未取代的 PENDING → CLAIMED，防双设备同时领到同一单 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE LocalBridgeTask t
            SET t.status = :claimed,
                t.claimedAt = :claimedAt,
                t.leaseUntil = :leaseUntil,
                t.claimedByDeviceKey = :deviceKey
            WHERE t.id = :id
              AND t.status = :pending
              AND (t.superseded = FALSE OR t.superseded IS NULL)
            """)
    int claimIfPending(
            @Param("id") Long id,
            @Param("claimed") LocalBridgeTask.Status claimed,
            @Param("pending") LocalBridgeTask.Status pending,
            @Param("claimedAt") LocalDateTime claimedAt,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("deviceKey") String deviceKey
    );

    /** 租约过期：CLAIMED → PENDING（已 superseded 的不回收） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE LocalBridgeTask t
            SET t.status = :pending,
                t.claimedAt = NULL,
                t.leaseUntil = NULL,
                t.claimedByDeviceKey = NULL
            WHERE t.status = :claimed
              AND (t.superseded = FALSE OR t.superseded IS NULL)
              AND t.leaseUntil IS NOT NULL
              AND t.leaseUntil < :now
            """)
    int reclaimExpiredLeases(
            @Param("now") LocalDateTime now,
            @Param("pending") LocalBridgeTask.Status pending,
            @Param("claimed") LocalBridgeTask.Status claimed
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE LocalBridgeTask t
            SET t.status = :pending,
                t.claimedAt = NULL,
                t.leaseUntil = NULL,
                t.claimedByDeviceKey = NULL
            WHERE t.userAgentId = :userAgentId
              AND t.status = :claimed
              AND (t.superseded = FALSE OR t.superseded IS NULL)
              AND t.leaseUntil IS NOT NULL
              AND t.leaseUntil < :now
            """)
    int reclaimExpiredLeasesForAgent(
            @Param("userAgentId") Long userAgentId,
            @Param("now") LocalDateTime now,
            @Param("pending") LocalBridgeTask.Status pending,
            @Param("claimed") LocalBridgeTask.Status claimed
    );
}
