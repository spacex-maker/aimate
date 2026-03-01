-- ------------------------------------------------------------
-- 迁移：assistant 回复多版本表（重试后保留历史，上下文用最新）
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS agent_assistant_message_versions (
    id                         BIGINT    NOT NULL AUTO_INCREMENT,
    agent_session_message_id  BIGINT    NOT NULL COMMENT 'FK → agent_session_messages.id（assistant 条）',
    version                    INT       NOT NULL COMMENT '版本号，从 1 递增',
    content                    LONGTEXT  NOT NULL,
    create_time                DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_message (agent_session_message_id),
    CONSTRAINT fk_version_message FOREIGN KEY (agent_session_message_id) REFERENCES agent_session_messages(id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='assistant 回复历史版本；当前展示与上下文均用 session_messages.content（最新）';
