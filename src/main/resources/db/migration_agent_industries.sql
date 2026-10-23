-- Agent 招聘：行业归类（手工/部署脚本执行；MySQL 8 不支持 ADD COLUMN IF NOT EXISTS）
-- 行业 → 职业 → 目录商品
-- 若列已存在会报 Duplicate column，可忽略。

CREATE TABLE IF NOT EXISTS agent_industries (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(512) NULL,
    sort_order   INT          NOT NULL DEFAULT 100,
    enabled      TINYINT(1)   NOT NULL DEFAULT 1,
    create_time  DATETIME(6)  NOT NULL,
    update_time  DATETIME(6)  NOT NULL,
    version      INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_agent_industries_code (code)
);

-- 已存在时跳过：Duplicate column name 'industry_id'
ALTER TABLE agent_careers
    ADD COLUMN industry_id BIGINT NULL;

-- 已存在时跳过
CREATE INDEX idx_agent_careers_industry_id ON agent_careers (industry_id);
