-- 多用户多 Agent 群组

CREATE TABLE IF NOT EXISTS chat_groups (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id       VARCHAR(64)  NOT NULL,
    title           VARCHAR(128) NOT NULL,
    avatar_url      VARCHAR(512) NULL,
    owner_user_id   BIGINT       NOT NULL,
    created_by      BIGINT       NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    create_time     DATETIME(6)  NOT NULL,
    update_time     DATETIME(6)  NOT NULL,
    version         INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_chat_groups_public_id (public_id),
    KEY idx_chat_groups_owner (owner_user_id)
);

CREATE TABLE IF NOT EXISTS group_members (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id            BIGINT       NOT NULL,
    member_type         VARCHAR(16)  NOT NULL,
    user_id             BIGINT       NULL,
    user_agent_id       BIGINT       NULL,
    role                VARCHAR(16)  NOT NULL,
    agent_reply_mode    VARCHAR(32)  NULL,
    joined_at           DATETIME(6)  NOT NULL,
    muted_until         DATETIME(6)  NULL,
    create_time         DATETIME(6)  NOT NULL,
    update_time         DATETIME(6)  NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_group_user (group_id, user_id),
    UNIQUE KEY uk_group_agent (group_id, user_agent_id),
    KEY idx_group_members_group (group_id)
);

CREATE TABLE IF NOT EXISTS group_messages (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id            BIGINT       NOT NULL,
    sender_type         VARCHAR(16)  NOT NULL,
    sender_user_id      BIGINT       NULL,
    sender_agent_id     BIGINT       NULL,
    content             LONGTEXT     NULL,
    reply_to_id         BIGINT       NULL,
    client_msg_id       VARCHAR(64)  NULL,
    create_time         DATETIME(6)  NOT NULL,
    update_time         DATETIME(6)  NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    KEY idx_group_messages_group_id (group_id, id),
    KEY idx_group_messages_group_time (group_id, create_time)
);

CREATE TABLE IF NOT EXISTS group_attachments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id          BIGINT       NOT NULL,
    group_id            BIGINT       NOT NULL,
    kind                VARCHAR(16)  NOT NULL,
    url                 VARCHAR(1024) NOT NULL,
    mime                VARCHAR(128) NULL,
    size_bytes          BIGINT       NULL,
    title               VARCHAR(256) NULL,
    meta_json           TEXT         NULL,
    create_time         DATETIME(6)  NOT NULL,
    update_time         DATETIME(6)  NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    KEY idx_group_att_msg (message_id),
    KEY idx_group_att_group_kind (group_id, kind, id)
);

CREATE TABLE IF NOT EXISTS group_message_reads (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id          BIGINT       NOT NULL,
    group_id            BIGINT       NOT NULL,
    member_id           BIGINT       NOT NULL,
    read_at             DATETIME(6)  NOT NULL,
    points_spent        BIGINT       NOT NULL DEFAULT 0 COMMENT '本条已读对应消耗积分；人类为0，Agent为分析消耗',
    create_time         DATETIME(6)  NOT NULL,
    update_time         DATETIME(6)  NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_group_msg_read (message_id, member_id),
    KEY idx_group_reads_group (group_id, member_id)
);
