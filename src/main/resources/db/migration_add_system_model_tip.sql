-- 模型提示：选模型时显示在名称旁（如 DeepSeek 非高峰半价）。
ALTER TABLE system_models
    ADD COLUMN tip VARCHAR(128) NULL COMMENT '选模型时名称旁提示，如非高峰半价时段' AFTER description;

-- DeepSeek 官方定价（api-docs.deepseek.com/quick_start/pricing）：
-- 高峰：UTC 周一至周五 01:00–04:00、06:00–10:00（中国法定节假日除外）
-- 北京时间对应：工作日 09:00–12:00、14:00–18:00
-- 其余时段（含周末与中国法定节假日全天）为非高峰，价格为高峰的一半。
UPDATE system_models
SET tip = '非高峰半价 · 工作日北京时间 9–12 / 14–18 外',
    update_time = NOW()
WHERE provider = 'deepseek'
  AND model_id IN ('deepseek-flash', 'deepseek-v4-pro')
  AND (tip IS NULL OR tip = '');
