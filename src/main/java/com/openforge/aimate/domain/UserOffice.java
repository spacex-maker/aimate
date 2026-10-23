package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "user_offices")
public class UserOffice extends BaseEntity {

    public enum Status {
        PROVISIONING,
        ACTIVE,
        STOPPING,
        STOPPED,
        EXPIRED,
        FAILED
    }

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.PROVISIONING;

    @Column(name = "cvm_instance_id", length = 64)
    private String cvmInstanceId;

    @Column(name = "private_ip", length = 64)
    private String privateIp;

    /** 仅服务端持有，鉴权 office-agent */
    @Column(name = "office_token", length = 128)
    private String officeToken;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "provision_error", length = 1024)
    private String provisionError;

    @Column(name = "last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    @Column(name = "last_idle_tick_at")
    private LocalDateTime lastIdleTickAt;
}
