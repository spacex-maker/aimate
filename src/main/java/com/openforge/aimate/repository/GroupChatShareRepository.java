package com.openforge.aimate.repository;

import com.openforge.aimate.domain.GroupChatShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupChatShareRepository extends JpaRepository<GroupChatShare, Long> {
    Optional<GroupChatShare> findByShareCode(String shareCode);

    boolean existsByShareCode(String shareCode);
}
