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
              AND (:q IS NULL OR :q = '' OR LOWER(k.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(k.summary, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(k.tags, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(k.publicId) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY k.sortOrder ASC, k.id ASC
            """)
    Page<KnowledgeBase> search(
            @Param("categoryId") Long categoryId,
            @Param("enabled") Boolean enabled,
            @Param("q") String q,
            Pageable pageable
    );
}
