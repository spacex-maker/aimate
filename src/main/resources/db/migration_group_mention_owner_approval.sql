-- Agent：被 @ 时是否请示所属人类；审批卡消息 kind；审批表
ALTER TABLE group_members
    ADD COLUMN require_owner_approval_on_mention TINYINT(1) NOT NULL DEFAULT 0
    AFTER others_may_mention;

ALTER TABLE group_messages
    ADD COLUMN message_kind VARCHAR(32) NOT NULL DEFAULT 'CHAT'
    AFTER client_msg_id;

CREATE TABLE IF NOT EXISTS group_mention_approvals (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    group_id              BIGINT       NOT NULL,
    card_message_id       BIGINT       NOT NULL,
    trigger_message_id    BIGINT       NOT NULL,
    target_member_id      BIGINT       NOT NULL,
    target_user_agent_id  BIGINT       NOT NULL,
    requester_user_id     BIGINT       NULL,
    requester_agent_id    BIGINT       NULL,
    status                VARCHAR(16)  NOT NULL,
    decided_by_user_id    BIGINT       NULL,
    decided_at            DATETIME(6)  NULL,
    create_time           DATETIME(6)  NOT NULL,
    update_time           DATETIME(6)  NOT NULL,
    version               INT          NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gma_trigger_target (trigger_message_id, target_member_id),
    KEY idx_gma_group (group_id),
    KEY idx_gma_card (card_message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
