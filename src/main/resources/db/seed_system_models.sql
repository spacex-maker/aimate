-- 系统模型目录初始化：当前地表最强 / 主流推理模型，供用户切换底座。
-- 执行时机：首次部署或清空 system_models 后。重复执行只更新展示信息。
-- enabled、sort_order 由超管在模型管理里保存，种子脚本不得覆盖。
--
-- 调用方式：以下 base_url 均指向「OpenAI 兼容」的 Chat Completions 接口（POST .../chat/completions），
-- 当前 LlmClient 可统一调用，无需按厂商分支：
--   OpenAI / xAI / DeepSeek：原生即兼容；
--   Google Gemini：generativelanguage.googleapis.com/v1beta/openai；
--   Anthropic Claude：api.anthropic.com/v1（官方 OpenAI SDK 兼容层）。
--
-- DeepSeek tip 依据官方定价：非高峰为高峰半价；高峰为 UTC 工作日 01–04 / 06–10
-- （北京时间工作日 9–12 / 14–18）；周末与中国法定节假日全天为非高峰。

INSERT INTO system_models (provider, model_id, display_name, base_url, sort_order, enabled, description, tip, version, create_time, update_time)
VALUES
-- OpenAI（综合能力与生态最佳）
('openai', 'gpt-5.2', 'GPT-5.2 Pro', 'https://api.openai.com/v1', 10, 1, '当前最强全能型，推理与代码表现顶尖', NULL, 0, NOW(), NOW()),
('openai', 'gpt-5.1', 'GPT-5.1', 'https://api.openai.com/v1', 11, 1, '上一代旗舰，平衡性能与成本', NULL, 0, NOW(), NOW()),

-- Google（推理与长上下文）
('google', 'gemini-3.1-pro-preview', 'Gemini 3.1 Pro', 'https://generativelanguage.googleapis.com/v1beta/openai', 20, 1, '推理与多模态领先，百万级上下文', NULL, 0, NOW(), NOW()),
('google', 'gemini-3-flash-preview', 'Gemini 3 Flash', 'https://generativelanguage.googleapis.com/v1beta/openai', 21, 1, '前沿性能、更低时延与成本', NULL, 0, NOW(), NOW()),

-- Anthropic（安全与长文）
('anthropic', 'claude-opus-4-5-20251101', 'Claude 4.5 Opus', 'https://api.anthropic.com/v1', 30, 1, '逻辑与安全标杆，长文与代码优秀（OpenAI 兼容端点）', NULL, 0, NOW(), NOW()),

-- xAI（实时信息与情商）
('xai', 'grok-4-0709', 'Grok 4', 'https://api.x.ai/v1', 40, 1, '实时 X 数据，情商与时效性强', NULL, 0, NOW(), NOW()),
('xai', 'grok-4-fast-reasoning', 'Grok 4 Fast', 'https://api.x.ai/v1', 41, 1, 'Grok 4 推理版，2M 上下文', NULL, 0, NOW(), NOW()),

-- DeepSeek（2026-10 官方常用 ID：deepseek-flash / deepseek-v4-pro）
('deepseek', 'deepseek-flash', 'DeepSeek V4.1 Flash', 'https://api.deepseek.com/v1', 50, 1, '当前 Flash 主模型，支持思考与非思考、工具调用和视觉，100万上下文', '非高峰半价 · 工作日北京时间 9–12 / 14–18 外', 0, NOW(), NOW()),
('deepseek', 'deepseek-v4-pro', 'DeepSeek V4 Pro', 'https://api.deepseek.com/v1', 51, 1, 'V4 Pro，默认思考模式，工具调用，100万上下文', '非高峰半价 · 工作日北京时间 9–12 / 14–18 外', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  base_url = VALUES(base_url),
  description = VALUES(description),
  tip = COALESCE(VALUES(tip), tip),
  update_time = NOW();

UPDATE system_models SET supports_vision = 1 WHERE model_id = 'deepseek-flash';

-- 上下文窗口（若列已存在）
UPDATE system_models SET context_window_tokens = 128000 WHERE model_id IN ('gpt-5.2', 'gpt-5.1');
UPDATE system_models SET context_window_tokens = 1000000 WHERE provider = 'google';
UPDATE system_models SET context_window_tokens = 200000 WHERE provider = 'anthropic';
UPDATE system_models SET context_window_tokens = 131072 WHERE model_id = 'grok-4-0709';
UPDATE system_models SET context_window_tokens = 2000000 WHERE model_id = 'grok-4-fast-reasoning';
UPDATE system_models SET context_window_tokens = 1000000 WHERE provider = 'deepseek';
