-- 会话置顶 / 群资料 / 扫码入群

ALTER TABLE agent_sessions
    ADD COLUMN pinned_at DATETIME(6) NULL;

ALTER TABLE chat_groups
    ADD COLUMN announcement TEXT NULL,
    ADD COLUMN invite_code VARCHAR(32) NULL;

-- invite_code 唯一（允许历史 NULL；新建群会生成）
CREATE UNIQUE INDEX uk_chat_groups_invite_code ON chat_groups (invite_code);

ALTER TABLE group_members
    ADD COLUMN nickname VARCHAR(64) NULL,
    ADD COLUMN remark VARCHAR(128) NULL,
    ADD COLUMN pinned_at DATETIME(6) NULL,
    ADD COLUMN archived TINYINT(1) NOT NULL DEFAULT 0;
