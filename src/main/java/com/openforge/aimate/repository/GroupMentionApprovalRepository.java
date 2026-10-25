package com.openforge.aimate.repository;

import com.openforge.aimate.domain.GroupMentionApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupMentionApprovalRepository extends JpaRepository<GroupMentionApproval, Long> {

    Optional<GroupMentionApproval> findByTriggerMessageIdAndTargetMemberId(Long triggerMessageId, Long targetMemberId);

    Optional<GroupMentionApproval> findByIdAndGroupId(Long id, Long groupId);

    List<GroupMentionApproval> findByCardMessageIdIn(Collection<Long> cardMessageIds);

    Optional<GroupMentionApproval> findByCardMessageId(Long cardMessageId);
}
