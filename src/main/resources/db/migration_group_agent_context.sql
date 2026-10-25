-- 群内每个 Agent 独立上下文占用 / 压缩状态（与单聊 SessionState 同方案）
CREATE TABLE IF NOT EXISTS group_agent_contexts (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    group_id                 BIGINT       NOT NULL,
    user_agent_id            BIGINT       NOT NULL,
    context_summary          LONGTEXT     NULL,
    folded_through_message_id BIGINT      NULL,
    create_time              DATETIME(6)  NOT NULL,
    update_time              DATETIME(6)  NOT NULL,
    version                  INT          NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gac_group_agent (group_id, user_agent_id),
    KEY idx_gac_agent (user_agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
