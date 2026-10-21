-- 将已停用的 deepseek-chat / deepseek-reasoner 换成官方当前常用模型 ID。
-- 2026-10 文档：https://api-docs.deepseek.com/quick_start/pricing
--   deepseek-flash   = DeepSeek-V4.1-Flash
--   deepseek-v4-pro  = DeepSeek-V4-Pro-0813
-- deepseek-chat 与 deepseek-reasoner 已于 2026-07-24 停用。

DROP TEMPORARY TABLE IF EXISTS tmp_deepseek_rename;
CREATE TEMPORARY TABLE tmp_deepseek_rename (id BIGINT PRIMARY KEY, new_model_id VARCHAR(128));

INSERT INTO tmp_deepseek_rename (id, new_model_id)
SELECT id, 'deepseek-flash'
FROM system_models
WHERE provider = 'deepseek' AND model_id = 'deepseek-chat'
  AND NOT EXISTS (
    SELECT 1 FROM system_models x
    WHERE x.provider = 'deepseek' AND x.model_id = 'deepseek-flash'
  );

INSERT INTO tmp_deepseek_rename (id, new_model_id)
SELECT id, 'deepseek-v4-pro'
FROM system_models
WHERE provider = 'deepseek' AND model_id = 'deepseek-reasoner'
  AND NOT EXISTS (
    SELECT 1 FROM system_models x
    WHERE x.provider = 'deepseek' AND x.model_id = 'deepseek-v4-pro'
  );

UPDATE system_models s
JOIN tmp_deepseek_rename t ON s.id = t.id
SET s.model_id = t.new_model_id,
    s.display_name = IF(t.new_model_id = 'deepseek-flash', 'DeepSeek V4.1 Flash', 'DeepSeek V4 Pro'),
    s.base_url = 'https://api.deepseek.com/v1',
    s.description = IF(
      t.new_model_id = 'deepseek-flash',
      '当前 Flash 主模型，支持思考与非思考、工具调用和视觉，100万上下文',
      'V4 Pro，默认思考模式，工具调用，100万上下文'
    ),
    s.update_time = NOW();

UPDATE system_models
SET enabled = 0, update_time = NOW()
WHERE provider = 'deepseek'
  AND model_id IN ('deepseek-chat', 'deepseek-reasoner', 'deepseek-v4-flash', 'deepseek-v4-flash-vision-exp');

INSERT INTO system_models (provider, model_id, display_name, base_url, sort_order, enabled, description, version, create_time, update_time)
VALUES
('deepseek', 'deepseek-flash', 'DeepSeek V4.1 Flash', 'https://api.deepseek.com/v1', 50, 1, '当前 Flash 主模型，支持思考与非思考、工具调用和视觉，100万上下文', 0, NOW(), NOW()),
('deepseek', 'deepseek-v4-pro', 'DeepSeek V4 Pro', 'https://api.deepseek.com/v1', 51, 1, 'V4 Pro，默认思考模式，工具调用，100万上下文', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  base_url = VALUES(base_url),
  description = VALUES(description),
  update_time = NOW();

DROP TEMPORARY TABLE IF EXISTS tmp_deepseek_rename;
