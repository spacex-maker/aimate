package com.openforge.aimate.repository;

import com.openforge.aimate.domain.GroupAttachment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupAttachmentRepository extends JpaRepository<GroupAttachment, Long> {
    List<GroupAttachment> findByMessageId(Long messageId);
    List<GroupAttachment> findByMessageIdIn(List<Long> messageIds);
    Page<GroupAttachment> findByGroupIdAndKindOrderByIdDesc(Long groupId, GroupAttachment.Kind kind, Pageable pageable);
}
