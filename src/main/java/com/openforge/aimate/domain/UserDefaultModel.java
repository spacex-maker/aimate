package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 记录用户最近一次在对话输入框中选择的模型（系统模型或用户自有模型）。
 * 仅存 user 与来源/ID 映射，具体调用仍由 SystemModel + UserApiKey + SystemConfig 决定。
 */
@Getter
@Setter
@Entity
@Table(name = "user_default_models")
public class UserDefaultModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 模型来源：SYSTEM=系统模型表，USER_KEY=用户 API Key 表。
     */
    @Column(name = "source", nullable = false, length = 16)
    private String source;

    /**
     * source=SYSTEM 时：最近一次选择的系统模型。
     */
    @ManyToOne
    @JoinColumn(name = "system_model_id")
    private SystemModel systemModel;

    /**
     * source=USER_KEY 时：最近一次选择的用户自有模型（user_api_keys.id）。
     */
    @ManyToOne
    @JoinColumn(name = "user_api_key_id")
    private UserApiKey userApiKey;
}

