-- 迁移：会话列表软隐藏
ALTER TABLE agent_sessions
  ADD COLUMN hidden TINYINT(1) NOT NULL DEFAULT 0
  COMMENT '1=从最近会话列表隐藏（不物理删除）'
  AFTER current_assistant_message_id;
