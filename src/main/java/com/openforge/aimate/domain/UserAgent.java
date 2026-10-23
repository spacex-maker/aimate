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

@Getter
@Setter
@Entity
@Table(name = "user_agents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_agent_ext_key", columnNames = {"user_id", "external_agent_key"})
})
public class UserAgent extends BaseEntity {

    public enum Kind {
        SECRETARY,
        HIRED
    }

    public enum Status {
        PENDING_PROFILE,
        ACTIVE,
        EXPIRED,
        DISABLED
    }

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind = Kind.HIRED;

    @Column(name = "catalog_id")
    private Long catalogId;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private AgentTier tier;

    /** 展示用昵称（用户可改） */
    @Column(nullable = false, length = 128)
    private String name;

    /** 招聘/创建时的原始名称；昵称修改后仍保留供展示 */
    @Column(name = "original_name", length = 128)
    private String originalName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(length = 32)
    private String gender;

    @Column(name = "personality", length = 512)
    private String personality;

    /** 与 Soul external_agent_key 一致 */
    @Column(name = "external_agent_key", nullable = false, length = 128)
    private String externalAgentKey;

    @Column(name = "soul_agent_id")
    private Long soulAgentId;

    @Column(name = "soul_key_id", length = 64)
    private String soulKeyId;

    @Column(name = "soul_seat_id")
    private Long soulSeatId;

    /** Soul access token（服务端保管，勿下发前端） */
    @Column(name = "soul_access_token", length = 512)
    private String soulAccessToken;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.PENDING_PROFILE;

    @Column(name = "current_prompt_version", nullable = false)
    private int currentPromptVersion = 0;

    @Column(name = "default_system_model_id")
    private Long defaultSystemModelId;

    /** 每日积分消耗上限；null=不限制 */
    @Column(name = "daily_points_limit")
    private Long dailyPointsLimit;

    /** 执行方式：云端 LLM 或本地桥接 */
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 32)
    private AgentExecutionMode executionMode = AgentExecutionMode.CLOUD_LLM;

    /** SHA-256 hex of local bridge bearer token；仅 LOCAL_BRIDGE 使用 */
    @Column(name = "local_bridge_token_hash", length = 64, unique = true)
    private String localBridgeTokenHash;

    /** 令牌可见前缀，便于用户辨认（不含完整密钥） */
    @Column(name = "local_bridge_token_prefix", length = 24)
    private String localBridgeTokenPrefix;
}
