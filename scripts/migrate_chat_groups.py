#!/usr/bin/env python3
"""Apply chat groups migration on chat prod."""
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get  # noqa: E402

SQL_PATH = ROOT / "src" / "main" / "resources" / "db" / "migration_chat_groups.sql"


def main() -> int:
    data = load_merged()
    password = env_get(data, "DEPLOY_SSH_PASSWORD")
    user = env_get(data, "DEPLOY_SSH_USER", default="ubuntu")
    sql = "SET NAMES utf8mb4;\n" + SQL_PATH.read_text(encoding="utf-8")

    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    print(f"Connecting {user}@{HOST} ...")
    ssh.connect(HOST, username=user, password=password, timeout=30)
    remote = "/tmp/migration_chat_groups.sql"
    sftp = ssh.open_sftp()
    with sftp.file(remote, "w") as f:
        f.write(sql)
    sftp.close()
    stdin, stdout, stderr = ssh.exec_command(f"sudo mysql aimate < {remote}", timeout=120)
    out = stdout.read()
    err = stderr.read()
    code = stdout.channel.recv_exit_status()
    sys.stdout.buffer.write(out)
    if err:
        sys.stderr.buffer.write(err)
    stdin, stdout, stderr = ssh.exec_command(
        "sudo mysql aimate -e \"SHOW TABLES LIKE 'chat_groups'; SHOW TABLES LIKE 'group_%';\"",
        timeout=60,
    )
    sys.stdout.buffer.write(stdout.read())
    ssh.exec_command(f"rm -f {remote}")
    ssh.close()
    print("DONE" if code == 0 else f"exit={code}")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
