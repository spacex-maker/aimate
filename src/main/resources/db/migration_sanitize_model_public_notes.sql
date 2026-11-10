-- User-facing model picker: tip = param size (or pricing badge); description cleared of infra/relay notes.

UPDATE system_models
SET tip = '8B',
    description = NULL,
    update_time = NOW()
WHERE provider = 'aimodel' AND model_id IN ('qwen3-8b', 'qwen3:8b');

UPDATE system_models
SET tip = '177B MoE',
    description = NULL,
    context_window_tokens = 131072,
    update_time = NOW()
WHERE provider = 'aimodel' AND model_id = 'qwen3.8-flash-next';

-- Clear leftover ops prose from descriptions shown historically in chat UI.
UPDATE system_models
SET description = NULL,
    update_time = NOW()
WHERE description IS NOT NULL
  AND (
    description LIKE '%Aimodel%'
    OR description LIKE '%llama.cpp%'
    OR description LIKE '%API Key%'
    OR description LIKE '%n_ctx%'
    OR description LIKE '%中转%'
    OR description LIKE '%网关%'
    OR description LIKE '%OpenAI 兼容端点%'
  );