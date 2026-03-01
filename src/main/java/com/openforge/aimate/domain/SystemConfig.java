package com.openforge.aimate.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 系统配置项，用于存储全局配置（如 Tavily API Key）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "system_config", uniqueConstraints = @UniqueConstraint(name = "uq_system_config_key", columnNames = "config_key"))
public class SystemConfig extends BaseEntity {

    @Column(name = "config_key", nullable = false, length = 128)
    private String configKey;

    @Column(name = "config_value", length = 2048)
    private String configValue;

    @Column(name = "description", length = 256)
    private String description;
}
