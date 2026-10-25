-- 本机执行中间输出（与 thinking_text 分离；.partial / CDP answer → execution）
ALTER TABLE local_bridge_tasks ADD COLUMN execution_text MEDIUMTEXT NULL;
ALTER TABLE group_messages ADD COLUMN execution_text MEDIUMTEXT NULL;
