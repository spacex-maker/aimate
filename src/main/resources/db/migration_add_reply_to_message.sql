-- ------------------------------------------------------------
-- 迁移：会话表、消息表新增列（按条归属、并行多轮）
-- 已有库按顺序执行；若报 Duplicate column/key/constraint 说明已存在，跳过该条即可。
-- ------------------------------------------------------------

-- 0. agent_sessions：当前回答中的占位条 id（兼容用，多轮并行时以消息表为准）
ALTER TABLE agent_sessions
  ADD COLUMN current_assistant_message_id BIGINT NULL
  COMMENT 'FK → agent_session_messages.id，当前回答中的占位条'
  AFTER error_message;

-- 1. message_status（仅 assistant 使用：ANSWERING / DONE / INTERRUPTED）
ALTER TABLE agent_session_messages
  ADD COLUMN message_status VARCHAR(20) NULL
  COMMENT '仅 assistant 使用: ANSWERING=回答中 DONE=已完成 INTERRUPTED=已中断'
  AFTER tool_calls_json;

-- 2. reply_to_message_id（归属某条 assistant 回复，NULL=主序消息）
ALTER TABLE agent_session_messages
  ADD COLUMN reply_to_message_id BIGINT NULL
  COMMENT '归属某条 assistant 回复（工具消息等）；NULL=主序消息'
  AFTER message_status;

-- 3. 索引
ALTER TABLE agent_session_messages
  ADD INDEX idx_reply_to (reply_to_message_id);

-- 4. 外键（自引用）
ALTER TABLE agent_session_messages
  ADD CONSTRAINT fk_msg_reply_to
  FOREIGN KEY (reply_to_message_id) REFERENCES agent_session_messages(id) ON DELETE CASCADE;
