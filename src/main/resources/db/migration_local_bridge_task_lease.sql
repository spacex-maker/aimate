-- 本机任务：租约回收 + 幂等键（防重启卡死 / 重复入队）
-- MySQL 8；列已存在时 Duplicate column / Duplicate key 可忽略。

ALTER TABLE local_bridge_tasks
    MODIFY COLUMN group_id BIGINT NULL;

ALTER TABLE local_bridge_tasks
    MODIFY COLUMN trigger_message_id BIGINT NULL;

ALTER TABLE local_bridge_tasks
    ADD COLUMN session_id VARCHAR(64) NULL;

ALTER TABLE local_bridge_tasks
    ADD COLUMN lease_until DATETIME(6) NULL;

ALTER TABLE local_bridge_tasks
    ADD COLUMN claimed_by_device_key VARCHAR(64) NULL;

ALTER TABLE local_bridge_tasks
    ADD COLUMN idempotency_key VARCHAR(128) NULL;

CREATE UNIQUE INDEX uk_lbt_agent_idempotency
    ON local_bridge_tasks (user_agent_id, idempotency_key);

CREATE INDEX idx_lbt_lease_claimed
    ON local_bridge_tasks (status, lease_until);
