package com.openforge.aimate.repository;

import com.openforge.aimate.domain.UserToolSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserToolSettingsRepository extends JpaRepository<UserToolSettings, Long> {

    Optional<UserToolSettings> findByUserId(Long userId);

    /** 按 userId 直接更新四个开关，避免读实体再保存导致的乐观锁冲突（快速连续点击）。 */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserToolSettings s SET s.memoryEnabled = :mem, s.webSearchEnabled = :web, s.createToolEnabled = :create, s.scriptExecEnabled = :script WHERE s.userId = :userId")
    int updateFlagsByUserId(
            @Param("userId") Long userId,
            @Param("mem") boolean mem,
            @Param("web") boolean web,
            @Param("create") boolean create,
            @Param("script") boolean script
    );
}
