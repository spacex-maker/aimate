-- 群：是否允许普通成员邀请用户/Agent（群主与管理员始终可邀请）
ALTER TABLE chat_groups
    ADD COLUMN members_invite_enabled TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '是否允许普通成员邀请；0=仅群主/管理员可邀请';
