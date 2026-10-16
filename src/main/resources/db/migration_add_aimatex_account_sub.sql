-- OIDC 统一登录：Account subject + 本地密码可空
ALTER TABLE users
  MODIFY COLUMN password_hash VARCHAR(255) NULL COMMENT '密码哈希；OIDC 用户可为空';

ALTER TABLE users
  ADD COLUMN aimatex_account_sub VARCHAR(64) NULL COMMENT 'AIMATEX Account OIDC sub'
  AFTER email;

ALTER TABLE users
  ADD UNIQUE KEY uk_users_aimatex_account_sub (aimatex_account_sub);
