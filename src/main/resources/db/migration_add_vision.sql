-- 视觉：模型能力标记 + 用户消息图片附件
ALTER TABLE system_models
    ADD COLUMN supports_vision TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否接受图片输入' AFTER enabled;

ALTER TABLE agent_session_messages
    ADD COLUMN attachments_json LONGTEXT NULL COMMENT '用户消息图片 data URL 的 JSON 数组' AFTER content;

UPDATE system_models SET supports_vision = 1 WHERE model_id = 'deepseek-flash';
