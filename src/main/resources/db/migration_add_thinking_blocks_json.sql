-- 迁移：assistant 消息增加标准化思考+工具调用时间线字段
ALTER TABLE agent_session_messages
  ADD COLUMN thinking_blocks_json LONGTEXT NULL
  COMMENT '仅 assistant：思考+工具调用时间线 StreamBlock JSON'
  AFTER thinking_content;
