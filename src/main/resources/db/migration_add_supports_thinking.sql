-- 系统模型：是否支持思考模式；用户首选：是否开启思考
ALTER TABLE system_models
    ADD COLUMN supports_thinking TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否支持思考模式（前端展示开关）' AFTER supports_vision;

ALTER TABLE user_default_models
    ADD COLUMN thinking_enabled TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '用户是否开启思考模式（仅对 supports_thinking 模型生效）' AFTER user_api_key_id;

-- 按厂商真实能力打标（运营台也可改）
-- DeepSeek：flash / v4-pro 支持思考开关；reasoner 为旧思考端点（已退役但仍可兼容）
-- Qwen3：本地网关 think 路由
UPDATE system_models
SET supports_thinking = 1
WHERE model_id IN (
        'qwen3-8b',
        'qwen3:8b',
        'deepseek-flash',
        'deepseek-v4-flash',
        'deepseek-v4-flash-vision-exp',
        'deepseek-v4-pro',
        'deepseek-reasoner'
    )
   OR model_id LIKE 'deepseek-reasoner%';

UPDATE system_models
SET supports_thinking = 0
WHERE model_id IN ('deepseek-chat');