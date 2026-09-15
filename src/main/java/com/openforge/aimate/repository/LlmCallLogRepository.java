package com.openforge.aimate.repository;

import com.openforge.aimate.domain.LlmCallLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, Long> {

    Page<LlmCallLog> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    @Query("""
            select l.userId,
                   coalesce(sum(l.promptTokens), 0),
                   coalesce(sum(l.completionTokens), 0),
                   coalesce(sum(l.totalTokens), 0),
                   coalesce(sum(l.estimatedCostUsd), 0.0),
                   count(l)
            from LlmCallLog l
            where (:userId is null or l.userId = :userId)
              and (:from is null or l.createTime >= :from)
              and (:to is null or l.createTime <= :to)
              and l.userId is not null
            group by l.userId
            order by coalesce(sum(l.totalTokens), 0) desc
            """)
    List<Object[]> aggregateUsage(@Param("userId") Long userId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    @Query("""
            select l from LlmCallLog l
            where l.userId = :userId
              and (:from is null or l.createTime >= :from)
              and (:to is null or l.createTime <= :to)
            order by l.createTime desc
            """)
    List<LlmCallLog> findDetails(@Param("userId") Long userId,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 Pageable pageable);
}
