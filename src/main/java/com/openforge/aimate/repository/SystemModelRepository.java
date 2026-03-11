package com.openforge.aimate.repository;

import com.openforge.aimate.domain.SystemModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemModelRepository extends JpaRepository<SystemModel, Long> {

    /** 仅返回启用的系统模型，按 sort_order 升序，供前端「切换模型」列表使用 */
    List<SystemModel> findByEnabledTrueOrderBySortOrderAsc();

    /** 管理员：返回全部系统模型（含已关闭），按 sort_order 升序 */
    List<SystemModel> findAllByOrderBySortOrderAsc();

    /** 按模型 ID 查找任一系统模型（优先用于与 agent.llm.primary/fallback.model 对齐） */
    Optional<SystemModel> findFirstByModelId(String modelId);
}
