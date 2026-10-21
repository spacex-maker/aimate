-- Multi-Agent P1（JPA ddl-auto=update 也会建表；本文件作文档/手工迁移参考）
-- 会话绑定 Agent
ALTER TABLE agent_sessions ADD COLUMN IF NOT EXISTS user_agent_id BIGINT NULL;

-- 说明：user_profiles / agent_careers / agent_catalog / agent_tier_models /
-- user_agents / agent_prompt_versions / agent_rent_orders 由实体自动建表。
