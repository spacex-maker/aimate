-- 系统 Agent（知识库策展等）。生产 ddl-auto=update 也会建表；本文件作文档/手工迁移参考。

CREATE TABLE IF NOT EXISTS system_agents (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    type                VARCHAR(32)  NOT NULL DEFAULT 'KB_CURATOR',
    code                VARCHAR(64)  NOT NULL,
    name                VARCHAR(128) NOT NULL,
    description         VARCHAR(512) NULL,
    enabled             TINYINT(1)   NOT NULL DEFAULT 1,
    schedule_hour       INT          NOT NULL DEFAULT 3,
    schedule_minute     INT          NOT NULL DEFAULT 0,
    system_model_id     BIGINT       NULL,
    config_json         MEDIUMTEXT   NULL,
    last_run_at         DATETIME(6)  NULL,
    last_run_status     VARCHAR(32)  NULL,
    last_run_summary    VARCHAR(1024) NULL,
    running             TINYINT(1)   NOT NULL DEFAULT 0,
    create_time         DATETIME(6)  NOT NULL,
    update_time         DATETIME(6)  NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_system_agents_code (code),
    KEY idx_system_agents_enabled_type (enabled, type)
);

CREATE TABLE IF NOT EXISTS system_agent_runs (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id            BIGINT       NOT NULL,
    trigger_type        VARCHAR(16)  NOT NULL DEFAULT 'SCHEDULE',
    status              VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',
    started_at          DATETIME(6)  NOT NULL,
    finished_at         DATETIME(6)  NULL,
    summary             VARCHAR(1024) NULL,
    detail_json         MEDIUMTEXT   NULL,
    create_time         DATETIME(6)  NOT NULL,
    update_time         DATETIME(6)  NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    KEY idx_system_agent_runs_agent (agent_id, id)
);
