-- 办公室租用（CVM for Agents）
-- MySQL 8：列已存在时 Duplicate column 可忽略

CREATE TABLE IF NOT EXISTS office_plans (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(64)  NOT NULL,
    name                VARCHAR(128) NOT NULL,
    area_sqm            INT          NOT NULL,
    instance_type       VARCHAR(64)  NOT NULL,
    cpu_desc            VARCHAR(64)  NULL,
    mem_desc            VARCHAR(64)  NULL,
    disk_gb             INT          NOT NULL DEFAULT 50,
    price_day_points    BIGINT       NOT NULL,
    price_month_points  BIGINT       NOT NULL,
    price_year_points   BIGINT       NOT NULL,
    description         VARCHAR(512) NULL,
    enabled             BIT(1)       NOT NULL,
    sort_order          INT          NOT NULL DEFAULT 100,
    create_time         DATETIME(6)  NOT NULL,
    update_time         DATETIME(6)  NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_office_plans_code (code)
);

CREATE TABLE IF NOT EXISTS user_offices (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    plan_id             BIGINT       NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    cvm_instance_id     VARCHAR(64)  NULL,
    private_ip          VARCHAR(64)  NULL,
    office_token        VARCHAR(128) NULL,
    expires_at          DATETIME(6)  NULL,
    provision_error     VARCHAR(1024) NULL,
    last_heartbeat_at   DATETIME(6)  NULL,
    last_idle_tick_at   DATETIME(6)  NULL,
    create_time         DATETIME(6)  NOT NULL,
    update_time         DATETIME(6)  NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    KEY idx_user_offices_user (user_id),
    KEY idx_user_offices_status (status)
);

CREATE TABLE IF NOT EXISTS office_rent_orders (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no            VARCHAR(64)  NOT NULL,
    user_id             BIGINT       NOT NULL,
    plan_id             BIGINT       NOT NULL,
    user_office_id      BIGINT       NULL,
    order_type          VARCHAR(16)  NOT NULL,
    period              VARCHAR(16)  NOT NULL,
    total_points        BIGINT       NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    meta_json           TEXT         NULL,
    create_time         DATETIME(6)  NOT NULL,
    update_time         DATETIME(6)  NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_office_rent_orders_no (order_no),
    KEY idx_office_rent_orders_user (user_id)
);

CREATE TABLE IF NOT EXISTS agent_daily_points (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_agent_id       BIGINT       NOT NULL,
    user_id             BIGINT       NOT NULL,
    day                 DATE         NOT NULL,
    spent_points        BIGINT       NOT NULL DEFAULT 0,
    create_time         DATETIME(6)  NOT NULL,
    update_time         DATETIME(6)  NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_agent_daily_points (user_agent_id, day)
);

ALTER TABLE user_agents ADD COLUMN daily_points_limit BIGINT NULL;
