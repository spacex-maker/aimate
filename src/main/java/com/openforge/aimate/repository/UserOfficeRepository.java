package com.openforge.aimate.repository;

import com.openforge.aimate.domain.UserOffice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserOfficeRepository extends JpaRepository<UserOffice, Long> {
    Optional<UserOffice> findFirstByUserIdAndStatusInOrderByIdDesc(Long userId, List<UserOffice.Status> statuses);
    List<UserOffice> findByStatus(UserOffice.Status status);
    List<UserOffice> findByUserIdOrderByIdDesc(Long userId);
}
