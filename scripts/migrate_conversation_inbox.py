#!/usr/bin/env python3
"""Safe apply conversation inbox migration on chat prod."""
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get  # noqa: E402

SAFE_SQL = r"""
SET NAMES utf8mb4;

-- agent_sessions.pinned_at
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='agent_sessions' AND COLUMN_NAME='pinned_at');
SET @sql := IF(@c=0, 'ALTER TABLE agent_sessions ADD COLUMN pinned_at DATETIME(6) NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- chat_groups.announcement
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat_groups' AND COLUMN_NAME='announcement');
SET @sql := IF(@c=0, 'ALTER TABLE chat_groups ADD COLUMN announcement TEXT NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- chat_groups.invite_code
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat_groups' AND COLUMN_NAME='invite_code');
SET @sql := IF(@c=0, 'ALTER TABLE chat_groups ADD COLUMN invite_code VARCHAR(32) NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @i := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat_groups' AND INDEX_NAME='uk_chat_groups_invite_code');
SET @sql := IF(@i=0, 'CREATE UNIQUE INDEX uk_chat_groups_invite_code ON chat_groups (invite_code)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- group_members fields
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='group_members' AND COLUMN_NAME='nickname');
SET @sql := IF(@c=0, 'ALTER TABLE group_members ADD COLUMN nickname VARCHAR(64) NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='group_members' AND COLUMN_NAME='remark');
SET @sql := IF(@c=0, 'ALTER TABLE group_members ADD COLUMN remark VARCHAR(128) NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='group_members' AND COLUMN_NAME='pinned_at');
SET @sql := IF(@c=0, 'ALTER TABLE group_members ADD COLUMN pinned_at DATETIME(6) NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='group_members' AND COLUMN_NAME='archived');
SET @sql := IF(@c=0, 'ALTER TABLE group_members ADD COLUMN archived TINYINT(1) NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
"""


def main() -> int:
    data = load_merged()
    password = env_get(data, "DEPLOY_SSH_PASSWORD")
    user = env_get(data, "DEPLOY_SSH_USER", default="ubuntu")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    print(f"Connecting {user}@{HOST} ...")
    ssh.connect(HOST, username=user, password=password, timeout=30)
    remote = "/tmp/migration_conversation_inbox.sql"
    sftp = ssh.open_sftp()
    with sftp.file(remote, "w") as f:
        f.write(SAFE_SQL)
    sftp.close()
    _, stdout, stderr = ssh.exec_command(f"sudo mysql aimate < {remote}", timeout=120)
    print(stdout.read().decode())
    err = stderr.read().decode()
    if err:
        print(err)
    code = stdout.channel.recv_exit_status()
    _, stdout, _ = ssh.exec_command(
        "sudo mysql aimate -e \"SHOW COLUMNS FROM group_members LIKE 'remark'; "
        "SHOW COLUMNS FROM chat_groups LIKE 'invite_code'; "
        "SHOW COLUMNS FROM agent_sessions LIKE 'pinned_at';\"",
        timeout=60,
    )
    print(stdout.read().decode())
    ssh.exec_command(f"rm -f {remote}")
    ssh.close()
    print("DONE" if code == 0 else f"exit={code}")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
