package com.openforge.aimate.repository;

import com.openforge.aimate.domain.SystemModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemModelRepository extends JpaRepository<SystemModel, Long> {

    /** 仅返回启用的系统模型，按 sort_order 升序，供前端「切换模型」列表使用 */
    List<SystemModel> findByEnabledTrueOrderBySortOrderAsc();

    /** 管理员：返回全部系统模型（含已关闭），按 sort_order 升序 */
    List<SystemModel> findAllByOrderBySortOrderAsc();
}
