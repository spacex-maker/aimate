-- 上下文压缩：pin 的消息永不 exclude_from_context
ALTER TABLE agent_session_messages
    ADD COLUMN pinned TINYINT(1) NOT NULL DEFAULT 0
        COMMENT 'true=钉住，压缩时保留原文送模'
        AFTER exclude_from_context;
