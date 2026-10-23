package com.openforge.aimate.repository;

import com.openforge.aimate.domain.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    List<GroupMember> findByGroupIdOrderByIdAsc(Long groupId);
    List<GroupMember> findByGroupIdAndMemberType(Long groupId, GroupMember.MemberType type);
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);
    Optional<GroupMember> findByGroupIdAndUserAgentId(Long groupId, Long userAgentId);
    Optional<GroupMember> findByIdAndGroupId(Long id, Long groupId);
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
}
