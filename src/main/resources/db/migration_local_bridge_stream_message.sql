-- 本机任务流式回传：绑定群消息 id，增量原地更新
ALTER TABLE local_bridge_tasks
    ADD COLUMN stream_message_id BIGINT NULL;
