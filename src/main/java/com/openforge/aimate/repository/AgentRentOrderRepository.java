package com.openforge.aimate.repository;

import com.openforge.aimate.domain.AgentRentOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRentOrderRepository extends JpaRepository<AgentRentOrder, Long> {
    Page<AgentRentOrder> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);
}
