package com.openforge.aimate.repository;

import com.openforge.aimate.domain.GroupMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, Long> {
    List<GroupMessage> findByGroupIdAndIdLessThanOrderByIdDesc(Long groupId, Long beforeId, Pageable pageable);
    List<GroupMessage> findByGroupIdOrderByIdDesc(Long groupId, Pageable pageable);

    @Query("""
            SELECT m FROM GroupMessage m
            WHERE m.groupId = :groupId
              AND m.createTime >= :start AND m.createTime < :end
            ORDER BY m.id ASC
            """)
    List<GroupMessage> findByGroupIdAndDay(
            @Param("groupId") Long groupId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
            SELECT DATE(create_time) AS d FROM group_messages
            WHERE group_id = :groupId
            GROUP BY DATE(create_time)
            ORDER BY d DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<java.sql.Date> findMessageDays(@Param("groupId") Long groupId, @Param("limit") int limit);

    List<GroupMessage> findByGroupIdAndIdGreaterThanOrderByIdAsc(Long groupId, Long afterId, Pageable pageable);
}
