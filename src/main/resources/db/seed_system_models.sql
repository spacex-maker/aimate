-- 系统模型目录初始化：当前地表最强 / 主流推理模型，供用户切换底座。
-- 执行时机：首次部署或清空 system_models 后。ON DUPLICATE KEY UPDATE 保证可重复执行。
--
-- 调用方式：以下 base_url 均指向「OpenAI 兼容」的 Chat Completions 接口（POST .../chat/completions），
-- 当前 LlmClient 可统一调用，无需按厂商分支：
--   OpenAI / xAI / DeepSeek：原生即兼容；
--   Google Gemini：generativelanguage.googleapis.com/v1beta/openai；
--   Anthropic Claude：api.anthropic.com/v1（官方 OpenAI SDK 兼容层）。

INSERT INTO system_models (provider, model_id, display_name, base_url, sort_order, enabled, description, version, create_time, update_time)
VALUES
-- OpenAI（综合能力与生态最佳）
('openai', 'gpt-5.2', 'GPT-5.2 Pro', 'https://api.openai.com/v1', 10, 1, '当前最强全能型，推理与代码表现顶尖', 0, NOW(), NOW()),
('openai', 'gpt-5.1', 'GPT-5.1', 'https://api.openai.com/v1', 11, 1, '上一代旗舰，平衡性能与成本', 0, NOW(), NOW()),

-- Google（推理与长上下文）
('google', 'gemini-3.1-pro-preview', 'Gemini 3.1 Pro', 'https://generativelanguage.googleapis.com/v1beta/openai', 20, 1, '推理与多模态领先，百万级上下文', 0, NOW(), NOW()),
('google', 'gemini-3-flash-preview', 'Gemini 3 Flash', 'https://generativelanguage.googleapis.com/v1beta/openai', 21, 1, '前沿性能、更低时延与成本', 0, NOW(), NOW()),

-- Anthropic（安全与长文）
('anthropic', 'claude-opus-4-5-20251101', 'Claude 4.5 Opus', 'https://api.anthropic.com/v1', 30, 1, '逻辑与安全标杆，长文与代码优秀（OpenAI 兼容端点）', 0, NOW(), NOW()),

-- xAI（实时信息与情商）
('xai', 'grok-4-0709', 'Grok 4', 'https://api.x.ai/v1', 40, 1, '实时 X 数据，情商与时效性强', 0, NOW(), NOW()),
('xai', 'grok-4-fast-reasoning', 'Grok 4 Fast', 'https://api.x.ai/v1', 41, 1, 'Grok 4 推理版，2M 上下文', 0, NOW(), NOW()),

-- DeepSeek（性价比与推理）
('deepseek', 'deepseek-reasoner', 'DeepSeek R1', 'https://api.deepseek.com/v1', 50, 1, '深度推理，性价比高，适合复杂推理', 0, NOW(), NOW()),
('deepseek', 'deepseek-chat', 'DeepSeek Chat', 'https://api.deepseek.com/v1', 51, 1, '通用对话，成本友好', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  base_url = VALUES(base_url),
  sort_order = VALUES(sort_order),
  enabled = VALUES(enabled),
  description = VALUES(description),
  update_time = NOW();
