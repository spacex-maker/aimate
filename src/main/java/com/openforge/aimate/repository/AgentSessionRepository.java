package com.openforge.aimate.repository;

import com.openforge.aimate.domain.AgentSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentSessionRepository extends JpaRepository<AgentSession, Long> {

    Optional<AgentSession> findBySessionId(String sessionId);

    /** 当前用户最近会话，按创建时间倒序，用于首页最近会话列表（排除已隐藏会话） */
    List<AgentSession> findByUserIdAndHiddenFalseOrderByCreateTimeDesc(Long userId, Pageable pageable);

    /** 活跃会话：优先按用户最近发消息时间，其次更新时间（发消息后列表上浮） */
    List<AgentSession> findByUserIdAndHiddenFalseOrderByLastUserMessageAtDescUpdateTimeDesc(
            Long userId, Pageable pageable);

    /** 已归档（hidden）会话，按更新时间倒序 */
    List<AgentSession> findByUserIdAndHiddenTrueOrderByUpdateTimeDesc(Long userId, Pageable pageable);

    List<AgentSession> findByStatus(AgentSession.SessionStatus status);

    /** 重启后：将仍有线程的会话置为 IDLE（进程已退出）。 */
    @Modifying
    @Query(value = "UPDATE agent_sessions SET status = 'IDLE', error_message = :msg WHERE status IN ('RUNNING', 'PAUSED', 'ACTIVE')", nativeQuery = true)
    int markActiveOrPausedAsIdle(@Param("msg") String msg);

    /** 兼容旧数据：将已废弃状态统一为 IDLE。 */
    @Modifying
    @Query(value = "UPDATE agent_sessions SET status = 'IDLE' WHERE status IN ('PENDING', 'COMPLETED', 'FAILED')", nativeQuery = true)
    int migrateLegacyStatusToIdle();

    /** 所有属于某个用户的会话（用于长期记忆迁移）。 */
    List<AgentSession> findByUserId(Long userId);

    List<AgentSession> findByUserIdAndUserAgentIdAndHiddenFalseOrderByUpdateTimeDesc(
            Long userId, Long userAgentId, Pageable pageable);

    List<AgentSession> findByUserIdAndUserAgentIdAndHiddenFalseOrderByLastUserMessageAtDescUpdateTimeDesc(
            Long userId, Long userAgentId, Pageable pageable);

    /** Agent 默认工具开关变更后：所有单聊取消会话覆盖，重新跟随 Agent。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AgentSession s SET s.toolSettingsOverridden = false WHERE s.userAgentId = :userAgentId AND s.toolSettingsOverridden = true")
    int clearToolSettingsOverrides(@Param("userAgentId") Long userAgentId);
}
