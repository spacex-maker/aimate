-- ============================================================
-- OpenForgeX — aimate  Database Schema
-- Engine : MySQL 8.0+
-- Charset: utf8mb4  (full Unicode, emoji-safe)
-- ============================================================

CREATE DATABASE IF NOT EXISTS aimate
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE aimate;

-- ------------------------------------------------------------
-- users
--
-- Basic system users for login / ownership.
-- Passwords are stored as strong one-way hashes (BCrypt).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    username        VARCHAR(64)     NOT NULL COMMENT '登录名，唯一',
    email           VARCHAR(128)             COMMENT '邮箱，唯一，可选',
    password_hash   VARCHAR(255)    NOT NULL COMMENT '密码哈希（BCrypt 等）',
    display_name    VARCHAR(128)             COMMENT '展示昵称',
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | DISABLED',
    last_login_time DATETIME                 COMMENT '最近登录时间',

    version         INT             NOT NULL DEFAULT 0 COMMENT 'JPA @Version 乐观锁计数器',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='系统用户表，用于登录与审计';


-- ------------------------------------------------------------
-- user_api_keys
--
-- Stores per-user API keys for any third-party provider
-- (OpenAI, DeepSeek, Anthropic, Azure, Milvus cloud, etc.).
--
-- Design points:
--   * key_value is stored as-is; production deployments should
--     encrypt it at the application layer before INSERT.
--   * provider is a free-form label (openai / deepseek / anthropic …)
--   * key_type groups keys by usage: LLM | EMBEDDING | VECTOR_DB | OTHER
--   * base_url lets users point to self-hosted / Azure proxy endpoints
--   * is_default marks which key is used by the agent loop for that
--     (user, provider, key_type) combination
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_api_keys (
    id          BIGINT          NOT NULL AUTO_INCREMENT,

    user_id     BIGINT UNSIGNED NOT NULL COMMENT 'FK → users.id',
    provider    VARCHAR(64)     NOT NULL COMMENT 'openai | deepseek | anthropic | azure | cohere | custom …',
    key_type    VARCHAR(32)     NOT NULL DEFAULT 'LLM'
                                COMMENT 'LLM | EMBEDDING | VECTOR_DB | OTHER',
    label       VARCHAR(128)             COMMENT '用户自定义备注，如"公司账号"',
    key_value   VARCHAR(512)    NOT NULL COMMENT 'API Key 原文（生产环境应加密存储）',
    base_url    VARCHAR(512)             COMMENT '自定义 Base URL，为空则使用厂商默认地址',
    model       VARCHAR(128)             COMMENT '该 Key 默认使用的模型，可选',
    is_default  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '1=该(user,provider,key_type)的默认 Key',
    is_active   TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '0=软删除',

    version     INT             NOT NULL DEFAULT 0,
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_apikey_user     (user_id),
    INDEX idx_apikey_provider (user_id, provider, key_type),
    CONSTRAINT fk_apikey_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户第三方 API Key 配置表';


-- ------------------------------------------------------------
-- user_embedding_models
--
-- Per-user embedding model configurations.
-- KEY DESIGN RULE: each (provider, model_name) pair gets its own
-- Milvus collection (derived as collection_name).  Vectors from
-- different models are NOT inter-searchable even if dim matches.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_embedding_models (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    user_id         BIGINT UNSIGNED NOT NULL COMMENT 'FK → users.id',
    name            VARCHAR(128)    NOT NULL COMMENT '用户备注名，如"本地 nomic"',
    provider        VARCHAR(32)     NOT NULL COMMENT 'openai | ollama | azure | custom',
    model_name      VARCHAR(128)    NOT NULL COMMENT 'text-embedding-3-small | nomic-embed-text …',
    api_key         VARCHAR(512)             COMMENT '本地部署可为 NULL',
    base_url        VARCHAR(512)    NOT NULL COMMENT 'https://api.openai.com/v1 或 http://localhost:11434',
    dimension       INT             NOT NULL COMMENT '向量维度，决定 Milvus collection',
    collection_name VARCHAR(128)    NOT NULL COMMENT 'Milvus collection，格式：memories_{model_sanitized}_{dim}',
    max_tokens      INT             NOT NULL DEFAULT 8192 COMMENT '最大输入 token 数',
    is_default      TINYINT(1)      NOT NULL DEFAULT 0,
    is_active       TINYINT(1)      NOT NULL DEFAULT 1,

    version         INT             NOT NULL DEFAULT 0,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_emb_user    (user_id),
    INDEX idx_emb_default (user_id, is_default, is_active),
    CONSTRAINT fk_emb_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户向量嵌入模型配置，支持 OpenAI / Ollama 等多厂商';


-- ------------------------------------------------------------
-- agent_tools
--
-- Stores every tool the Agent can invoke, including dynamically
-- generated "right-brain" scripts.  No Java restart required
-- when a new row is inserted — the left brain polls this table.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_tools (
    id              BIGINT          NOT NULL AUTO_INCREMENT,

    tool_name       VARCHAR(128)    NOT NULL COMMENT '唯一机器可读标识符，即 LLM function_calling 中的 name 字段',
    tool_description TEXT           NOT NULL COMMENT '发送给 LLM 的自然语言描述，说明何时应调用此工具',
    input_schema    TEXT            NOT NULL COMMENT 'JSON Schema 对象，描述工具入参，直接注入 LLM tools 数组',

    tool_type       VARCHAR(32)     NOT NULL COMMENT 'JAVA_NATIVE | PYTHON_SCRIPT | NODE_SCRIPT | SHELL_CMD',
    script_content  LONGTEXT                 COMMENT '右脑生成的脚本原文；JAVA_NATIVE 类型为 NULL',
    entry_point     VARCHAR(256)             COMMENT 'JAVA_NATIVE: Spring bean 名; 脚本类型: 建议文件名',

    is_active       TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '软删除标志；0 = 禁用但保留历史',
    is_system       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '1=系统工具，仅本机执行不走 Docker（如 recall_memory/store_memory/tavily_search）',

    -- ── 审计 + 乐观锁凭证 ────────────────────────────────
    version         INT             NOT NULL DEFAULT 0 COMMENT 'JPA @Version 乐观锁计数器',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_tool_name (tool_name),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent 可调用工具注册表；支持运行时热插拔';


-- ------------------------------------------------------------
-- agent_sessions
--
-- One row = one complete Agent thinking session.
-- The Java process is stateless; crashing and restarting is safe
-- because all cognitive state lives here.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_sessions (
    id               BIGINT         NOT NULL AUTO_INCREMENT,

    user_id          BIGINT UNSIGNED NULL     COMMENT 'FK → users.id，会话所有者',
    session_id       VARCHAR(64)    NOT NULL COMMENT '外部传入的 UUID，供上层系统关联',
    task_description TEXT           NOT NULL COMMENT '用户原始任务描述',

    status           VARCHAR(32)    NOT NULL DEFAULT 'PENDING'
                                    COMMENT 'PENDING | RUNNING | PAUSED | COMPLETED | FAILED',

    current_plan     TEXT                    COMMENT 'JSON 序列化的 List<PlanStep>；Planner 首次运行后填充',
    context_window   LONGTEXT                COMMENT 'JSON 序列化的 List<Message>；每轮迭代追加后写回',

    iteration_count  INT            NOT NULL DEFAULT 0 COMMENT 'Agent 循环计数，防止无限循环',
    result           TEXT                    COMMENT '上一轮最终答案',
    error_message    TEXT                    COMMENT '上一轮错误或中断说明',
    current_assistant_message_id BIGINT NULL COMMENT 'FK → agent_session_messages.id，当前回答中的占位条，可被用户中断',

    -- ── 审计 + 乐观锁凭证 ────────────────────────────────
    version          INT            NOT NULL DEFAULT 0 COMMENT 'JPA @Version 乐观锁计数器',
    create_time      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_session_id (session_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent 思考会话状态表；Java 进程无状态，全量持久化于此';


-- ------------------------------------------------------------
-- agent_session_messages
--
-- 会话内对话消息表，按条存储，便于分页、检索与归档。
-- 与 agent_sessions.context_window 可并存：context_window 为当前上下文
-- 的紧凑快照，本表为完整历史；也可逐步改为仅从本表构建 context。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_session_messages (
    id                BIGINT          NOT NULL AUTO_INCREMENT,

    agent_session_id  BIGINT          NOT NULL COMMENT 'FK → agent_sessions.id',
    seq               INT             NOT NULL COMMENT '会话内顺序，从 0 开始',

    role              VARCHAR(20)     NOT NULL COMMENT 'system | user | assistant | tool',
    content           LONGTEXT        NULL     COMMENT '正文；assistant 仅 tool_calls 时可为空',
    tool_call_id      VARCHAR(64)     NULL     COMMENT 'role=tool 时对应 ToolCall.id',
    tool_calls_json   TEXT            NULL     COMMENT 'role=assistant 时的 tool_calls 数组 JSON',
    message_status    VARCHAR(20)     NULL     COMMENT '仅 assistant 使用: ANSWERING=回答中 DONE=已完成 INTERRUPTED=已中断',
    thinking_content  LONGTEXT        NULL     COMMENT '仅 assistant：思考过程正文，前端可折叠展示',
    reply_to_message_id BIGINT        NULL     COMMENT '归属某条 assistant 回复（工具消息等）；NULL=主序消息',

    version           INT             NOT NULL DEFAULT 0,
    create_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_session_seq (agent_session_id, seq),
    INDEX idx_agent_session_id (agent_session_id),
    INDEX idx_reply_to (reply_to_message_id),
    CONSTRAINT fk_msg_session FOREIGN KEY (agent_session_id) REFERENCES agent_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_reply_to FOREIGN KEY (reply_to_message_id) REFERENCES agent_session_messages(id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='会话内消息表；reply_to_message_id 标识归属某条回复（并行多轮）';

-- ------------------------------------------------------------
-- agent_assistant_message_versions
-- assistant 回复的多版本；上下文给 AI 时始终用每条消息的最新版本（session_messages.content）。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_assistant_message_versions (
    id                     BIGINT    NOT NULL AUTO_INCREMENT,
    agent_session_message_id BIGINT   NOT NULL COMMENT 'FK → agent_session_messages.id（assistant 条）',
    version                 INT       NOT NULL COMMENT '版本号，从 1 递增',
    content                 LONGTEXT NOT NULL,
    create_time             DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_message (agent_session_message_id),
    CONSTRAINT fk_version_message FOREIGN KEY (agent_session_message_id) REFERENCES agent_session_messages(id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='assistant 回复历史版本；当前展示与上下文均用 session_messages.content（最新）';

-- ------------------------------------------------------------
-- system_config
-- 系统配置表：全局 Key 等（如 TAVILY_API_KEY）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS system_config (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    config_key      VARCHAR(128)    NOT NULL COMMENT '配置键，如 TAVILY_API_KEY',
    config_value    VARCHAR(2048)   NULL     COMMENT '配置值',
    description     VARCHAR(256)   NULL     COMMENT '说明',
    version         INT             NOT NULL DEFAULT 0,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_system_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='系统配置表';
