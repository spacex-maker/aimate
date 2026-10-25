-- 官方工具（user_id IS NULL）去重：MySQL 对 UNIQUE(tool_name, user_id) 允许多个 NULL，seed 重复执行会堆行。
-- 每个 tool_name 只保留 id 最小的一条；并增加 user_scope_key 真正防重。

DELETE t1 FROM agent_tools t1
INNER JOIN agent_tools t2
  ON t1.tool_name = t2.tool_name
 AND t1.user_id IS NULL
 AND t2.user_id IS NULL
 AND t1.id > t2.id;

-- 办公室重活改由代码按开通状态暴露；库内副本停用（create_tool 属官方库，不停用）
UPDATE agent_tools
SET is_active = 0, update_time = NOW()
WHERE user_id IS NULL
  AND tool_name IN (
    'install_container_package',
    'run_container_cmd',
    'write_container_file'
  );

-- 官方四件套描述刷新（中英兼顾，列表展示可读）
UPDATE agent_tools SET
  tool_description = '按自然语言检索长期记忆（用户画像、偏好、过往事实等）。需要回忆此前存过的信息时调用。',
  update_time = NOW()
WHERE user_id IS NULL AND tool_name = 'recall_memory' AND is_active = 1;

UPDATE agent_tools SET
  tool_description = '把重要、跨会话仍有用的事实写入长期记忆。仅对真正长期有用的信息调用，勿滥用。',
  update_time = NOW()
WHERE user_id IS NULL AND tool_name = 'store_memory' AND is_active = 1;

UPDATE agent_tools SET
  tool_description = '通过 Tavily 检索实时/事实信息（新闻、时事等）。用户自身已存信息请优先用 recall_memory。',
  update_time = NOW()
WHERE user_id IS NULL AND tool_name = 'tavily_search' AND is_active = 1;

UPDATE agent_tools SET
  tool_description = '由服务端发起 HTTP 请求（无需办公室）。适合 REST/健康检查/Soul 同步；禁止访问内网与 localhost。',
  update_time = NOW()
WHERE user_id IS NULL AND tool_name = 'http_request' AND is_active = 1;

-- 用 IFNULL(user_id,0) 生成作用域键，使官方工具名真正全局唯一
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'agent_tools'
    AND COLUMN_NAME = 'user_scope_key'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE agent_tools ADD COLUMN user_scope_key BIGINT GENERATED ALWAYS AS (IFNULL(user_id, 0)) STORED',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 替换唯一索引（旧索引对 NULL 无效）
SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'agent_tools'
    AND INDEX_NAME = 'uq_tool_name_scope'
);
SET @sql2 := IF(@idx = 0,
  'ALTER TABLE agent_tools DROP INDEX uq_tool_name_user, ADD UNIQUE KEY uq_tool_name_scope (tool_name, user_scope_key)',
  'SELECT 1');
PREPARE stmt2 FROM @sql2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;
