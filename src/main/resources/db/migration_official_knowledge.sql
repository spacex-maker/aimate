-- 官方知识库：行业/领域分类树 + 原子知识库 + 官方 Agent 关联 + 用户招聘快照

CREATE TABLE IF NOT EXISTS kb_categories (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id           BIGINT       NULL COMMENT '父分类；NULL=根（行业/领域）',
    code                VARCHAR(64)  NOT NULL,
    name                VARCHAR(128) NOT NULL,
    description         VARCHAR(512) NULL,
    sort_order          INT          NOT NULL DEFAULT 100,
    enabled             TINYINT(1)   NOT NULL DEFAULT 1,
    create_time         DATETIME(6)  NOT NULL,
    update_time         DATETIME(6)  NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_kb_categories_code (code),
    KEY idx_kb_cat_parent (parent_id, sort_order, id)
);

CREATE TABLE IF NOT EXISTS kb_knowledge_bases (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id           VARCHAR(40)  NOT NULL COMMENT '对外原子知识库 ID，如 kb_xxx',
    category_id         BIGINT       NOT NULL COMMENT '挂载分类（任意层级）',
    code                VARCHAR(64)  NULL,
    name                VARCHAR(128) NOT NULL,
    summary             VARCHAR(1024) NULL,
    content             MEDIUMTEXT   NULL COMMENT '知识正文（可后续切分向量）',
    tags                VARCHAR(512) NULL COMMENT '逗号分隔标签',
    sort_order          INT          NOT NULL DEFAULT 100,
    enabled             TINYINT(1)   NOT NULL DEFAULT 1,
    create_time         DATETIME(6)  NOT NULL,
    update_time         DATETIME(6)  NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_kb_public_id (public_id),
    UNIQUE KEY uk_kb_code (code),
    KEY idx_kb_category (category_id, sort_order, id)
);

CREATE TABLE IF NOT EXISTS agent_catalog_knowledge (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    catalog_id          BIGINT       NOT NULL,
    knowledge_base_id   BIGINT       NOT NULL,
    create_time         DATETIME(6)  NOT NULL,
    update_time         DATETIME(6)  NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_catalog_kb (catalog_id, knowledge_base_id),
    KEY idx_ack_kb (knowledge_base_id)
);

CREATE TABLE IF NOT EXISTS user_agent_knowledge (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_agent_id       BIGINT       NOT NULL,
    knowledge_base_id   BIGINT       NOT NULL,
    create_time         DATETIME(6)  NOT NULL,
    update_time         DATETIME(6)  NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_ua_kb (user_agent_id, knowledge_base_id),
    KEY idx_uak_kb (knowledge_base_id)
);
