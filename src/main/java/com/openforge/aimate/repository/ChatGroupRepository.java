package com.openforge.aimate.repository;

import com.openforge.aimate.domain.ChatGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, Long> {
    Optional<ChatGroup> findByPublicId(String publicId);

    Optional<ChatGroup> findByInviteCode(String inviteCode);

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
