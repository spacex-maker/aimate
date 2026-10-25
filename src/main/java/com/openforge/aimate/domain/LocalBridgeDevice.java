package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 本地桥接设备：同一 UserAgent（如全局唯一 Cursor）可挂多台设备。
 * 接入方式：客户端（Aimate Connector）或旧版提示词 worker。
 */
@Getter
@Setter
@Entity
@Table(name = "local_bridge_devices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_lbd_agent_device", columnNames = {"user_agent_id", "device_key"})
})
public class LocalBridgeDevice extends BaseEntity {

    public enum LinkMode {
        /** Aimate Connector 桌面客户端 */
        CLIENT,
        /** 提示词 / 脚本 worker */
        PROMPT
    }

    @Column(name = "user_agent_id", nullable = false)
    private Long userAgentId;

    /** 连接器安装级稳定 ID，或提示词设备登记 ID */
    @Column(name = "device_key", nullable = false, length = 64)
    private String deviceKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_mode", nullable = false, length = 16)
    private LinkMode linkMode = LinkMode.CLIENT;

    @Column(length = 128)
    private String hostname;

    @Column(length = 128)
    private String os;

    @Column(length = 32)
    private String arch;

    @Column(length = 64)
    private String label;

    @Column(name = "app_name", length = 64)
    private String appName;

    @Column(name = "app_path", length = 512)
    private String appPath;

    @Column(name = "connector_version", length = 32)
    private String connectorVersion;

    @Column(name = "career_code", length = 64)
    private String careerCode;

    /** 最近心跳；新建提示词设备可为 null（待首次上线） */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    /**
     * 用户在 Web 上手动关闭连接：为 true 时心跳不再刷新在线态，直至「允许重连」。
     */
    @Column(name = "user_disconnected", nullable = false)
    private boolean userDisconnected = false;
}
