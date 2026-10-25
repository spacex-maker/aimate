package com.openforge.aimate.repository;

import com.openforge.aimate.domain.ChatGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, Long> {
    Optional<ChatGroup> findByPublicId(String publicId);

    Optional<ChatGroup> findByInviteCode(String inviteCode);

    /**
     * 多 Agent 并发回复时避免对同一 ChatGroup 实体 save 触发乐观锁；
     * 直接 bump 活跃时间，不携带过期 version。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE chat_groups SET update_time = NOW(6), version = version + 1 WHERE id = :id", nativeQuery = true)
    int touchActivity(@Param("id") Long id);

    @Query("""
            SELECT g FROM ChatGroup g
            WHERE g.status = com.openforge.aimate.domain.ChatGroup.Status.ACTIVE
              AND g.id IN (
                SELECT m.groupId FROM GroupMember m
                WHERE m.memberType = com.openforge.aimate.domain.GroupMember.MemberType.USER
                  AND m.userId = :userId
              )
            ORDER BY g.updateTime DESC
            """)
    List<ChatGroup> findActiveForUser(@Param("userId") Long userId);
}
