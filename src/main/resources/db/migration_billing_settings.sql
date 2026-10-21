-- 计费全局规则：API 成本加成倍率 + 积分/元比例
INSERT INTO system_config (config_key, config_value, description, create_time, update_time)
VALUES
('CHAT_BILLING_MARKUP_MULTIPLIER', '4',
 '计费加成倍率。用户扣积分 = ceil(API成本CNY × 倍率 × 每元积分)。默认 4。',
 NOW(), NOW()),
('CHAT_POINTS_PER_YUAN', '1000',
 '积分与人民币比例：1 元 = N 积分。默认 1000。',
 NOW(), NOW())
ON DUPLICATE KEY UPDATE
  description = VALUES(description),
  update_time = NOW();
