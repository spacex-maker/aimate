package com.openforge.aimate.repository;

import com.openforge.aimate.domain.SessionMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionMessageRepository extends JpaRepository<SessionMessage, Long> {

    List<SessionMessage> findByAgentSession_IdOrderBySeqAsc(Long agentSessionId);

    /** 仅主序消息（user 或 reply_to 为空的 assistant），按 seq 降序，用于「最近 N 条」分页。 */
    @Query("SELECT m FROM SessionMessage m WHERE m.agentSession.id = :sessionId " +
           "AND (m.role = 'user' OR (m.role = 'assistant' AND m.replyToMessageId IS NULL)) " +
           "ORDER BY m.seq DESC")
    List<SessionMessage> findMainMessagesBySessionIdOrderBySeqDesc(@Param("sessionId") Long sessionId, Pageable pageable);

    /** 仅主序消息且 seq < beforeSeq，按 seq 降序取 N 条，用于「加载更多」向前分页。 */
    @Query("SELECT m FROM SessionMessage m WHERE m.agentSession.id = :sessionId AND m.seq < :beforeSeq " +
           "AND (m.role = 'user' OR (m.role = 'assistant' AND m.replyToMessageId IS NULL)) " +
           "ORDER BY m.seq DESC")
    List<SessionMessage> findMainMessagesBySessionIdAndSeqBeforeOrderBySeqDesc(
            @Param("sessionId") Long sessionId, @Param("beforeSeq") int beforeSeq, Pageable pageable);

    /** 某条回复的上下文：主序消息 seq≤maxSeq 或归属该回复的消息，按 seq 升序。 */
    @Query("SELECT m FROM SessionMessage m WHERE m.agentSession.id = :sessionId " +
           "AND ((m.replyToMessageId IS NULL AND m.seq <= :maxSeq) OR m.replyToMessageId = :replyToId) " +
           "ORDER BY m.seq ASC")
    List<SessionMessage> findContextForReply(@Param("sessionId") Long sessionId,
                                             @Param("maxSeq") int maxSeq,
                                             @Param("replyToId") Long replyToId);

    /** 上下文截止到某 seq（主序消息，用于重试时只取该条之前的上下文）。 */
    @Query("SELECT m FROM SessionMessage m WHERE m.agentSession.id = :sessionId AND m.replyToMessageId IS NULL AND m.seq <= :maxSeq ORDER BY m.seq ASC")
    List<SessionMessage> findContextUpToSeq(@Param("sessionId") Long sessionId, @Param("maxSeq") int maxSeq);

    /** 按会话 + seq 取一条（用于重试时找 user 的下一条 assistant）。 */
    java.util.Optional<SessionMessage> findByAgentSession_IdAndSeq(Long agentSessionId, int seq);

    /** 某条主 assistant 回复的附属消息（工具调用/结果），按 seq 升序，用于前端展示工具调用列表。 */
    List<SessionMessage> findByReplyToMessageIdOrderBySeqAsc(Long replyToMessageId);

    /** 会话内 ANSWERING 的 assistant 条数（用于判断是否还有在跑的回答）。 */
    @Query("SELECT COUNT(m) FROM SessionMessage m WHERE m.agentSession.id = :sessionId AND m.role = 'assistant' AND m.messageStatus = 'ANSWERING'")
    long countAnswering(@Param("sessionId") Long sessionId);

    /** 删除某会话下所有消息（如重置会话时），使用批量 DELETE 避免逐条删除导致的乐观锁冲突。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SessionMessage m WHERE m.agentSession.id = :agentSessionId")
    void deleteByAgentSessionId(@Param("agentSessionId") Long agentSessionId);
}
