package com.openforge.aimate.repository;

import com.openforge.aimate.domain.LocalBridgeDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocalBridgeDeviceRepository extends JpaRepository<LocalBridgeDevice, Long> {
    List<LocalBridgeDevice> findByUserAgentIdOrderByLastSeenAtDesc(Long userAgentId);

    List<LocalBridgeDevice> findByUserAgentId(Long userAgentId);

    Optional<LocalBridgeDevice> findByUserAgentIdAndDeviceKey(Long userAgentId, String deviceKey);

    void deleteByUserAgentId(Long userAgentId);
}
