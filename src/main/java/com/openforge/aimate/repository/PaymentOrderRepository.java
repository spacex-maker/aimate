package com.openforge.aimate.repository;

import com.openforge.aimate.domain.PaymentOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByOrderNo(String orderNo);

    Page<PaymentOrder> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    Page<PaymentOrder> findByStatusOrderByCreateTimeDesc(String status, Pageable pageable);

    Page<PaymentOrder> findAllByOrderByCreateTimeDesc(Pageable pageable);
}
