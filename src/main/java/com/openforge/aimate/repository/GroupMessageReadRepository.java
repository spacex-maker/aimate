package com.openforge.aimate.repository;

import com.openforge.aimate.domain.GroupMessageRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMessageReadRepository extends JpaRepository<GroupMessageRead, Long> {
    List<GroupMessageRead> findByMessageId(Long messageId);
    List<GroupMessageRead> findByMessageIdIn(List<Long> messageIds);
    Optional<GroupMessageRead> findByMessageIdAndMemberId(Long messageId, Long memberId);
}
