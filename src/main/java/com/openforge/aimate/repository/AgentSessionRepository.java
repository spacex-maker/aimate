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
}
