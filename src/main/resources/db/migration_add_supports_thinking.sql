-- 系统模型：是否支持思考模式；用户首选：是否开启思考
ALTER TABLE system_models
    ADD COLUMN supports_thinking TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否支持思考模式（前端展示开关）' AFTER supports_vision;

ALTER TABLE user_default_models
    ADD COLUMN thinking_enabled TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '用户是否开启思考模式（仅对 supports_thinking 模型生效）' AFTER user_api_key_id;

-- 已知支持思考的模型打标（运营台也可改）
UPDATE system_models
SET supports_thinking = 1
WHERE model_id IN ('qwen3-8b', 'qwen3:8b', 'deepseek-reasoner')
   OR model_id LIKE 'deepseek-reasoner%'
   OR LOWER(display_name) LIKE '%reasoner%';