-- 本机任务意图世代（epoch）：同群/会话新指令取代旧指令
-- MySQL 8；列已存在时 Duplicate column 可忽略。

ALTER TABLE local_bridge_tasks
    ADD COLUMN intent_epoch BIGINT NOT NULL DEFAULT 0;

ALTER TABLE local_bridge_tasks
    ADD COLUMN superseded TINYINT(1) NOT NULL DEFAULT 0;

-- 历史行：用触发消息 id 回填 epoch，便于新旧比较
UPDATE local_bridge_tasks
SET intent_epoch = COALESCE(trigger_message_id, id)
WHERE intent_epoch = 0;

CREATE INDEX idx_lbt_scope_epoch_group
    ON local_bridge_tasks (user_agent_id, group_id, intent_epoch);

CREATE INDEX idx_lbt_scope_epoch_session
    ON local_bridge_tasks (user_agent_id, session_id, intent_epoch);
