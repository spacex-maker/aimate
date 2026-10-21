-- Chat 积分计费：余额、模型倍率/成本价、账本、套餐、订单

ALTER TABLE users
    ADD COLUMN points_balance BIGINT NOT NULL DEFAULT 0 COMMENT 'Chat 平台积分余额' AFTER last_login_time;

ALTER TABLE system_models
    ADD COLUMN points_per_1k_prompt_tokens INT NOT NULL DEFAULT 1 COMMENT '每1K prompt token 扣积分' AFTER tip,
    ADD COLUMN points_per_1k_completion_tokens INT NOT NULL DEFAULT 3 COMMENT '每1K completion token 扣积分' AFTER points_per_1k_prompt_tokens,
    ADD COLUMN cost_input_cny_per_1m_peak DECIMAL(12,4) NULL COMMENT '系统成本：百万input高峰CNY' AFTER points_per_1k_completion_tokens,
    ADD COLUMN cost_input_cny_per_1m_offpeak DECIMAL(12,4) NULL COMMENT '系统成本：百万input空闲CNY' AFTER cost_input_cny_per_1m_peak,
    ADD COLUMN cost_output_cny_per_1m_peak DECIMAL(12,4) NULL COMMENT '系统成本：百万output高峰CNY' AFTER cost_input_cny_per_1m_offpeak,
    ADD COLUMN cost_output_cny_per_1m_offpeak DECIMAL(12,4) NULL COMMENT '系统成本：百万output空闲CNY' AFTER cost_output_cny_per_1m_peak;

ALTER TABLE llm_call_logs
    ADD COLUMN estimated_cost_cny DECIMAL(14,6) NULL COMMENT '估算系统成本CNY' AFTER estimated_cost_usd,
    ADD COLUMN points_charged BIGINT NULL COMMENT '本次扣除积分' AFTER estimated_cost_cny;

CREATE TABLE IF NOT EXISTS point_ledger (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    user_id           BIGINT UNSIGNED NOT NULL,
    delta             BIGINT          NOT NULL COMMENT '正=入账，负=扣费',
    balance_after     BIGINT          NOT NULL,
    reason            VARCHAR(32)     NOT NULL COMMENT 'CHAT_DEDUCT|RECHARGE|ADMIN_ADJUST|SYSTEM_GIFT',
    ref_type          VARCHAR(32)              COMMENT 'ORDER|LLM_CALL|ADMIN',
    ref_id            VARCHAR(64)              COMMENT '关联业务 id',
    session_id        VARCHAR(64)              NULL,
    llm_call_log_id   BIGINT                   NULL,
    remark            VARCHAR(256)             NULL,
    version           INT             NOT NULL DEFAULT 0,
    create_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_point_ledger_user_time (user_id, create_time),
    INDEX idx_point_ledger_reason (reason),
    CONSTRAINT fk_point_ledger_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Chat 积分流水';

CREATE TABLE IF NOT EXISTS point_packages (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(128)    NOT NULL,
    price_yuan      DECIMAL(12,2)   NOT NULL,
    points          BIGINT          NOT NULL COMMENT '充值积分',
    bonus_points    BIGINT          NOT NULL DEFAULT 0 COMMENT '赠送积分',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1,
    sort_order      INT             NOT NULL DEFAULT 100,
    description     VARCHAR(256)             NULL,
    version         INT             NOT NULL DEFAULT 0,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_packages_name (name),
    INDEX idx_point_packages_enabled_sort (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='积分充值套餐';

CREATE TABLE IF NOT EXISTS payment_orders (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    order_no         VARCHAR(64)     NOT NULL,
    user_id          BIGINT UNSIGNED NOT NULL,
    product_type     VARCHAR(32)     NOT NULL DEFAULT 'POINT_PACK',
    product_id       BIGINT          NOT NULL,
    amount_yuan      DECIMAL(12,2)   NOT NULL,
    payment_method   VARCHAR(64)     NOT NULL DEFAULT 'wechat_pay_xunhu',
    status           VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    transaction_id   VARCHAR(128)             NULL,
    meta_json        LONGTEXT                 NULL,
    paid_at          DATETIME                 NULL,
    version          INT             NOT NULL DEFAULT 0,
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_orders_no (order_no),
    INDEX idx_payment_orders_user_time (user_id, create_time),
    INDEX idx_payment_orders_status (status),
    CONSTRAINT fk_payment_orders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Chat 支付订单';

-- DeepSeek 官方 CNY 价（缓存未命中口径）+ 默认积分倍率
UPDATE system_models SET
  points_per_1k_prompt_tokens = 1,
  points_per_1k_completion_tokens = 3,
  cost_input_cny_per_1m_peak = 3.0,
  cost_input_cny_per_1m_offpeak = 1.5,
  cost_output_cny_per_1m_peak = 9.0,
  cost_output_cny_per_1m_offpeak = 4.5
WHERE model_id = 'deepseek-flash';

UPDATE system_models SET
  points_per_1k_prompt_tokens = 3,
  points_per_1k_completion_tokens = 9,
  cost_input_cny_per_1m_peak = 9.0,
  cost_input_cny_per_1m_offpeak = 4.5,
  cost_output_cny_per_1m_peak = 27.0,
  cost_output_cny_per_1m_offpeak = 13.5
WHERE model_id = 'deepseek-v4-pro';

INSERT INTO point_packages (name, price_yuan, points, bonus_points, enabled, sort_order, description, version, create_time, update_time)
VALUES
('体验包', 6.00, 600, 0, 1, 10, '入门体验', 0, NOW(), NOW()),
('标准包', 30.00, 3000, 300, 1, 20, '赠送 300 积分', 0, NOW(), NOW()),
('进阶包', 98.00, 10000, 1500, 1, 30, '赠送 1500 积分', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE update_time = NOW();

INSERT INTO system_config (config_key, config_value, description, version, create_time, update_time)
VALUES
('CHAT_REGISTER_GIFT_POINTS', '100', '新用户注册赠送积分', 0, NOW(), NOW()),
('XUNHU_APPID', '201906176878', '虎皮椒支付 AppId', 0, NOW(), NOW()),
('XUNHU_SECRET', '38850dae45ebe916f4b354bcf660f799', '虎皮椒支付 Secret', 0, NOW(), NOW()),
('XUNHU_GATEWAY_URL', 'https://api.xunhupay.com/payment/do.html', '虎皮椒网关', 0, NOW(), NOW()),
('XUNHU_NOTIFY_URL', 'https://chat-api.aimatex.com/api/pay/wechat/notify', 'Chat 支付回调', 0, NOW(), NOW()),
('XUNHU_RETURN_URL', 'https://chat.aimatex.com/app', '支付完成跳转', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  config_value = IF(config_value IS NULL OR config_value = '', VALUES(config_value), config_value),
  description = VALUES(description);

-- 存量用户一次性赠送（仅尚无流水且余额为 0）
UPDATE users u
SET u.points_balance = 100
WHERE u.points_balance = 0
  AND NOT EXISTS (SELECT 1 FROM point_ledger pl WHERE pl.user_id = u.id);
