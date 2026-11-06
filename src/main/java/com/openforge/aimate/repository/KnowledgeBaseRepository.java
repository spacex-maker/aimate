package com.openforge.aimate.repository;

import com.openforge.aimate.domain.KnowledgeBase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    Optional<KnowledgeBase> findByPublicId(String publicId);

    Optional<KnowledgeBase> findByCode(String code);

    List<KnowledgeBase> findByCategoryIdOrderBySortOrderAscIdAsc(Long categoryId);

    List<KnowledgeBase> findByIdIn(Collection<Long> ids);

    @Query("""
            SELECT k FROM KnowledgeBase k
            WHERE (:categoryId IS NULL OR k.categoryId = :categoryId)
              AND (:enabled IS NULL OR k.enabled = :enabled)
              AND (:tag IS NULL OR :tag = '' OR LOWER(COALESCE(k.tags, '')) LIKE LOWER(CONCAT('%', :tag, '%')))
              AND (:code IS NULL OR :code = '' OR LOWER(COALESCE(k.code, '')) LIKE LOWER(CONCAT('%', :code, '%')))
              AND (:q IS NULL OR :q = '' OR LOWER(k.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(k.summary, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(k.tags, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(k.code, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(k.publicId) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY k.sortOrder ASC, k.id ASC
            """)
    Page<KnowledgeBase> search(
            @Param("categoryId") Long categoryId,
            @Param("enabled") Boolean enabled,
            @Param("q") String q,
            @Param("tag") String tag,
            @Param("code") String code,
            Pageable pageable
    );

    /** 策展候选：启用库，可按分类过滤，按更新时间升序（越久未更新越优先）。 */
    @Query("""
            SELECT k FROM KnowledgeBase k
            WHERE k.enabled = true
              AND (:categoryIdsEmpty = true OR k.categoryId IN :categoryIds)
            ORDER BY k.updateTime ASC, k.id ASC
            """)
    List<KnowledgeBase> findCuratorCandidates(
            @Param("categoryIdsEmpty") boolean categoryIdsEmpty,
            @Param("categoryIds") Collection<Long> categoryIds,
            Pageable pageable
    );
}