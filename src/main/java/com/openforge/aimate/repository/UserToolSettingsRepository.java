package com.openforge.aimate.repository;

import com.openforge.aimate.domain.UserToolSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserToolSettingsRepository extends JpaRepository<UserToolSettings, Long> {

    Optional<UserToolSettings> findByUserId(Long userId);
}
