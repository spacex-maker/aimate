-- 系统配置表：存储全局配置项（如第三方 API Key），key 唯一
CREATE TABLE IF NOT EXISTS system_config (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    config_key      VARCHAR(128)    NOT NULL COMMENT '配置键，如 TAVILY_API_KEY',
    config_value    VARCHAR(2048)   NULL     COMMENT '配置值，如 API Key 原文',
    description     VARCHAR(256)   NULL     COMMENT '说明',
    version         INT             NOT NULL DEFAULT 0,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_system_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='系统配置表，存放全局 Key 等';
