-- Chat: Qwen3.8-Flash-Next via Aimodel gateway (same amk_ account as qwen3-8b)
-- Idempotent: insert if missing; refresh pricing/context on re-run.

INSERT INTO system_models (
  provider, model_id, display_name, base_url, api_key_config_key,
  sort_order, enabled, supports_vision, supports_thinking, context_window_tokens,
  description, tip,
  points_per_1k_prompt_tokens, points_per_1k_completion_tokens,
  cost_input_cny_per_1m_peak, cost_input_cny_per_1m_offpeak,
  cost_output_cny_per_1m_peak, cost_output_cny_per_1m_offpeak,
  version, create_time, update_time
)
SELECT
  'aimodel',
  'qwen3.8-flash-next',
  'Qwen3.8-Flash-Next',
  'https://aimodel-api.aimatex.com/v1',
  'DEEPSEEK_API_KEY_AIMODEL_CHAT',
  61,
  1,
  0,
  1,
  131072,
  'Aimodel 180B MoE（llama.cpp）。训练上下文 262K，当前服务端 n_ctx=131072。与 Qwen3-8B 共用同一 Aimodel API Key。',
  'Aimodel · 128K',
  2,
  4,
  20.0,
  20.0,
  40.0,
  40.0,
  0,
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM system_models
  WHERE provider = 'aimodel' AND model_id = 'qwen3.8-flash-next'
);

UPDATE system_models
SET
  display_name = 'Qwen3.8-Flash-Next',
  base_url = 'https://aimodel-api.aimatex.com/v1',
  api_key_config_key = 'DEEPSEEK_API_KEY_AIMODEL_CHAT',
  enabled = 1,
  supports_thinking = 1,
  context_window_tokens = 131072,
  description = 'Aimodel 180B MoE（llama.cpp）。训练上下文 262K，当前服务端 n_ctx=131072。与 Qwen3-8B 共用同一 Aimodel API Key。',
  tip = 'Aimodel · 128K',
  points_per_1k_prompt_tokens = 2,
  points_per_1k_completion_tokens = 4,
  cost_input_cny_per_1m_peak = 20.0,
  cost_input_cny_per_1m_offpeak = 20.0,
  cost_output_cny_per_1m_peak = 40.0,
  cost_output_cny_per_1m_offpeak = 40.0,
  update_time = NOW()
WHERE provider = 'aimodel' AND model_id = 'qwen3.8-flash-next';

-- Keep Aimodel Chat key description accurate for both models
UPDATE system_config
SET description = 'Aimodel Chat 专用 Key（qwen3-8b / qwen3.8-flash-next 共用同一 Aimodel 账户）',
    update_time = NOW()
WHERE config_key = 'DEEPSEEK_API_KEY_AIMODEL_CHAT';

UPDATE system_providers
SET description = 'AIMATEX Aimodel OpenAI-compat gateway（Chat 与 Aimodel 控制台共用 amk_ 账户）',
    update_time = NOW()
WHERE code = 'aimodel';