-- 群聊分享快照（公开只读链接）
CREATE TABLE IF NOT EXISTS group_chat_shares (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    share_code        VARCHAR(32)  NOT NULL,
    group_id          BIGINT       NOT NULL,
    group_public_id   VARCHAR(64)  NOT NULL,
    group_title       VARCHAR(256) NULL,
    created_by_user_id BIGINT      NOT NULL,
    message_count     INT          NOT NULL DEFAULT 0,
    payload_json      LONGTEXT     NOT NULL,
    create_time       DATETIME(6)  NOT NULL,
    update_time       DATETIME(6)  NOT NULL,
    version           INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_group_chat_shares_code (share_code),
    KEY idx_group_chat_shares_group (group_id)
);
