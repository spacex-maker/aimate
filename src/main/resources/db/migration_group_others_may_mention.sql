-- Agent：是否允许其他成员 @ 触发
ALTER TABLE group_members
    ADD COLUMN others_may_mention TINYINT(1) NOT NULL DEFAULT 1
    AFTER agent_reply_mode;
