package com.openforge.aimate.repository;

import com.openforge.aimate.domain.PointLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointLedgerRepository extends JpaRepository<PointLedger, Long> {

    Page<PointLedger> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    Page<PointLedger> findByUserIdAndReasonOrderByCreateTimeDesc(Long userId, String reason, Pageable pageable);
}
