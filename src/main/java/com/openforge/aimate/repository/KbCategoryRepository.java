package com.openforge.aimate.repository;

import com.openforge.aimate.domain.KbCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KbCategoryRepository extends JpaRepository<KbCategory, Long> {
    Optional<KbCategory> findByCode(String code);

    List<KbCategory> findAllByOrderBySortOrderAscIdAsc();

    List<KbCategory> findByParentIdIsNullOrderBySortOrderAscIdAsc();

    List<KbCategory> findByParentIdOrderBySortOrderAscIdAsc(Long parentId);

    List<KbCategory> findByEnabledTrueOrderBySortOrderAscIdAsc();

    long countByParentId(Long parentId);
}
