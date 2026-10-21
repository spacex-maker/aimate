-- 系统模型上下文窗口上限（token），供聊天输入框占用率圆环使用
ALTER TABLE system_models
    ADD COLUMN context_window_tokens INT NOT NULL DEFAULT 128000 COMMENT '模型上下文窗口上限（token）' AFTER supports_vision;

-- 按公开规格大致填入（可后续在管理端微调）
UPDATE system_models SET context_window_tokens = 128000 WHERE model_id IN ('gpt-5.2', 'gpt-5.1');
UPDATE system_models SET context_window_tokens = 1000000 WHERE provider = 'google';
UPDATE system_models SET context_window_tokens = 200000 WHERE provider = 'anthropic';
UPDATE system_models SET context_window_tokens = 131072 WHERE model_id = 'grok-4-0709';
UPDATE system_models SET context_window_tokens = 2000000 WHERE model_id = 'grok-4-fast-reasoning';
UPDATE system_models SET context_window_tokens = 1000000 WHERE provider = 'deepseek';
