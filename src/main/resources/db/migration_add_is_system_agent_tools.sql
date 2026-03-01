-- agent_tools 表增加 is_system 字段：系统工具仅本机执行，不走 Docker
-- 执行逻辑：recall_memory / store_memory / tavily_search 在 AgentLoopService 中按名称分支，不会进入脚本执行；此处标记便于区分与扩展。

ALTER TABLE agent_tools
ADD COLUMN is_system TINYINT(1) NOT NULL DEFAULT 0
COMMENT '1=系统工具，仅本机执行不走 Docker'
AFTER is_active;

-- 将已有内置工具标记为系统工具（若存在）
UPDATE agent_tools SET is_system = 1 WHERE tool_name IN ('recall_memory', 'store_memory', 'tavily_search');
