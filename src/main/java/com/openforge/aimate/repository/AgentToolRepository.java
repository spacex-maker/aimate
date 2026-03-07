package com.openforge.aimate.repository;

import com.openforge.aimate.domain.AgentTool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentToolRepository extends JpaRepository<AgentTool, Long> {

    /** 按名称查系统工具（user_id 为空）。 */
    Optional<AgentTool> findByToolNameAndUserIdIsNull(String toolName);

    /** 按名称与用户查用户自有工具。 */
    Optional<AgentTool> findByToolNameAndUserId(String toolName, Long userId);

    /** 所有启用的系统工具（供 Agent 加载 + 前端列表）。 */
    List<AgentTool> findByUserIdIsNullAndIsActiveTrue();

    /** 某用户所有启用的自定义工具。 */
    List<AgentTool> findByUserIdAndIsActiveTrue(Long userId);

    /** 某用户全部自定义工具（含未启用），用于管理列表。 */
    List<AgentTool> findByUserIdOrderByIdAsc(Long userId);
}
