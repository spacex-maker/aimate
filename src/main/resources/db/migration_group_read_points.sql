-- 群消息已读：记录 Agent 分析该条消息所消耗的积分（人类为 0）
-- 重复执行可能报 Duplicate column，部署脚本 check=False 可忽略
ALTER TABLE group_message_reads
    ADD COLUMN points_spent BIGINT NOT NULL DEFAULT 0 COMMENT '本条已读对应消耗积分；人类为0，Agent为分析消耗';
