#!/usr/bin/env python3
"""Apply office rental migration + ensure tables on chat prod."""
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get  # noqa: E402

SQL_PATH = ROOT / "src" / "main" / "resources" / "db" / "migration_office_rental.sql"


def main() -> int:
    data = load_merged()
    password = env_get(data, "DEPLOY_SSH_PASSWORD")
    user = env_get(data, "DEPLOY_SSH_USER", default="ubuntu")
    sql = SQL_PATH.read_text(encoding="utf-8")
    # MySQL 8: wrap ADD COLUMN so duplicate is ignored via procedure-less try
    safe = """
SET NAMES utf8mb4;
""" + sql.replace(
        "ALTER TABLE user_agents ADD COLUMN daily_points_limit BIGINT NULL;",
        """
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_agents' AND COLUMN_NAME = 'daily_points_limit'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE user_agents ADD COLUMN daily_points_limit BIGINT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
""",
    )

    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    print(f"Connecting {user}@{HOST} ...")
    ssh.connect(HOST, username=user, password=password, timeout=30)
    remote = "/tmp/migration_office_rental.sql"
    sftp = ssh.open_sftp()
    with sftp.file(remote, "w") as f:
        f.write(safe)
    sftp.close()
    stdin, stdout, stderr = ssh.exec_command(f"sudo mysql aimate < {remote}", timeout=120)
    out = stdout.read()
    err = stderr.read()
    code = stdout.channel.recv_exit_status()
    sys.stdout.buffer.write(out)
    if err:
        sys.stderr.buffer.write(err)
    # verify
    stdin, stdout, stderr = ssh.exec_command(
        "sudo mysql aimate -e \"SHOW TABLES LIKE 'office%'; SELECT COUNT(*) AS plans FROM office_plans;\"",
        timeout=60,
    )
    sys.stdout.buffer.write(stdout.read())
    ssh.exec_command(f"rm -f {remote}")
    ssh.close()
    print("DONE" if code == 0 else f"exit={code}")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
