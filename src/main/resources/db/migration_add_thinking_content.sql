-- 迁移：assistant 消息增加思考过程字段
ALTER TABLE agent_session_messages
  ADD COLUMN thinking_content LONGTEXT NULL
  COMMENT '仅 assistant：思考过程正文，前端可折叠展示'
  AFTER message_status;
