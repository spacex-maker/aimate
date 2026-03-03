package com.openforge.aimate.repository;

import com.openforge.aimate.domain.LlmCallLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, Long> {

    Page<LlmCallLog> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);
}

