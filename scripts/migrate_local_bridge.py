#!/usr/bin/env python3
"""Apply local_bridge DDL on the chat MySQL used by application-local.yml."""
from __future__ import annotations

import sys

try:
    import pymysql
except ImportError:
    print("pip install pymysql", file=sys.stderr)
    sys.exit(1)

HOST = "170.106.105.131"
USER = "admin"
PASSWORD = "xp43520!@#"
DB = "aimate"

STATEMENTS = [
    "ALTER TABLE agent_careers ADD COLUMN execution_mode VARCHAR(32) NOT NULL DEFAULT 'CLOUD_LLM'",
    "ALTER TABLE user_agents ADD COLUMN execution_mode VARCHAR(32) NOT NULL DEFAULT 'CLOUD_LLM'",
    "ALTER TABLE user_agents ADD COLUMN local_bridge_token_hash VARCHAR(64) NULL",
    "ALTER TABLE user_agents ADD COLUMN local_bridge_token_prefix VARCHAR(24) NULL",
    """CREATE UNIQUE INDEX uk_user_agents_local_bridge_token_hash
       ON user_agents (local_bridge_token_hash)""",
    """CREATE TABLE IF NOT EXISTS local_bridge_tasks (
        id                BIGINT AUTO_INCREMENT PRIMARY KEY,
        public_id         VARCHAR(40)  NOT NULL,
        user_agent_id     BIGINT       NOT NULL,
        group_id          BIGINT       NOT NULL,
        trigger_message_id BIGINT      NOT NULL,
        status            VARCHAR(16)  NOT NULL,
        instruction       TEXT         NULL,
        context_json      MEDIUMTEXT   NULL,
        result_text       MEDIUMTEXT   NULL,
        error_message     VARCHAR(1024) NULL,
        claimed_at        DATETIME(6)  NULL,
        completed_at      DATETIME(6)  NULL,
        create_time       DATETIME(6)  NOT NULL,
        update_time       DATETIME(6)  NOT NULL,
        version           INT          NOT NULL DEFAULT 0,
        UNIQUE KEY uk_local_bridge_tasks_public_id (public_id),
        KEY idx_lbt_agent_status (user_agent_id, status, id),
        KEY idx_lbt_trigger (user_agent_id, trigger_message_id)
    )""",
]


def main() -> int:
    conn = pymysql.connect(
        host=HOST,
        user=USER,
        password=PASSWORD,
        database=DB,
        charset="utf8mb4",
        autocommit=True,
    )
    try:
        with conn.cursor() as cur:
            for sql in STATEMENTS:
                try:
                    cur.execute(sql)
                    print("OK:", sql.splitlines()[0][:80])
                except pymysql.err.OperationalError as e:
                    # 1060 duplicate column, 1061 duplicate key name
                    if e.args and e.args[0] in (1060, 1061):
                        print("SKIP:", e.args[1])
                    else:
                        raise
        with conn.cursor() as cur:
            cur.execute(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                "WHERE TABLE_SCHEMA=%s AND TABLE_NAME='user_agents' "
                "AND COLUMN_NAME LIKE 'local_bridge%%' OR "
                "(TABLE_SCHEMA=%s AND TABLE_NAME='agent_careers' AND COLUMN_NAME='execution_mode')",
                (DB, DB),
            )
            print("columns:", [r[0] for r in cur.fetchall()])
            cur.execute("SHOW TABLES LIKE 'local_bridge_tasks'")
            print("table:", cur.fetchone())
    finally:
        conn.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
