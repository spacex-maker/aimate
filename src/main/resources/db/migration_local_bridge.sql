-- 本地 AI 桥接（Cursor / Codex 等）：职业执行模式 + Agent 鉴权令牌 + 任务队列
-- 手工/部署脚本执行；MySQL 8。列已存在时 Duplicate column 可忽略。

ALTER TABLE agent_careers
    ADD COLUMN execution_mode VARCHAR(32) NOT NULL DEFAULT 'CLOUD_LLM';

ALTER TABLE user_agents
    ADD COLUMN execution_mode VARCHAR(32) NOT NULL DEFAULT 'CLOUD_LLM';

ALTER TABLE user_agents
    ADD COLUMN local_bridge_token_hash VARCHAR(64) NULL;

ALTER TABLE user_agents
    ADD COLUMN local_bridge_token_prefix VARCHAR(24) NULL;

CREATE UNIQUE INDEX uk_user_agents_local_bridge_token_hash
    ON user_agents (local_bridge_token_hash);

CREATE TABLE IF NOT EXISTS local_bridge_tasks (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id         VARCHAR(40)  NOT NULL,
    user_agent_id     BIGINT       NOT NULL,
    group_id          BIGINT       NOT NULL,
    trigger_message_id BIGINT      NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    instruction       TEXT         NULL,
    context_json      MEDIUMTEXT   NULL,
    result_text       MEDIUMTEXT   NULL,
    error_message     VARCHAR(1024) NULL,
    claimed_at        DATETIME(6)  NULL,
    completed_at      DATETIME(6)  NULL,
    create_time       DATETIME(6)  NOT NULL,
    update_time       DATETIME(6)  NOT NULL,
    version           INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_local_bridge_tasks_public_id (public_id),
    KEY idx_lbt_agent_status (user_agent_id, status, id),
    KEY idx_lbt_trigger (user_agent_id, trigger_message_id)
);
