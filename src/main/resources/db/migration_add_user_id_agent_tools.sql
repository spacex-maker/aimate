-- agent_tools 用户级隔离：user_id 为空表示系统工具，非空表示该用户创建的工具
-- 唯一约束改为 (tool_name, user_id)，不同用户可有同名工具，系统工具全局唯一

ALTER TABLE agent_tools
ADD COLUMN user_id BIGINT NULL COMMENT 'NULL=系统工具，非空=该用户创建'
AFTER is_system;

-- 去掉原 tool_name 唯一约束（改为 (tool_name, user_id)）
ALTER TABLE agent_tools DROP INDEX uq_tool_name;

-- 新增联合唯一约束
ALTER TABLE agent_tools ADD UNIQUE KEY uq_tool_name_user (tool_name, user_id);

-- 已有数据视为系统工具，保持 user_id 为 NULL（已为 NULL）
-- 可选：将 is_system=1 的明确为系统工具
-- UPDATE agent_tools SET user_id = NULL WHERE is_system = 1;
