-- system_providers: one LLM vendor -> one API key
CREATE TABLE IF NOT EXISTS system_providers (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  api_key VARCHAR(2048) NULL,
  description VARCHAR(256) NULL,
  enabled BIT(1) NOT NULL DEFAULT b'1',
  sort_order INT NOT NULL DEFAULT 100,
  create_time DATETIME(6) NOT NULL,
  update_time DATETIME(6) NOT NULL,
  version INT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_system_providers_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;