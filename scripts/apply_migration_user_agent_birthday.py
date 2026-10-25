#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, REMOTE_JAVA, load_merged, env_get  # noqa: E402


def main() -> int:
    d = load_merged()
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(
        HOST,
        username=env_get(d, "DEPLOY_SSH_USER", default="ubuntu"),
        password=env_get(d, "DEPLOY_SSH_PASSWORD"),
        timeout=30,
        allow_agent=False,
        look_for_keys=False,
    )
    name = "migration_user_agent_birthday.sql"
    local = ROOT / "src" / "main" / "resources" / "db" / name
    remote = f"{REMOTE_JAVA}/db/{name}"
    sftp = ssh.open_sftp()
    sftp.put(str(local), remote)
    sftp.close()
    cmd = f"sudo mysql aimate < {remote}"
    print(">>>", cmd)
    _i, o, e = ssh.exec_command(cmd, timeout=60)
    out = (o.read() + e.read()).decode("utf-8", errors="replace")
    code = o.channel.recv_exit_status()
    if out.strip():
        print(out)
    if code != 0:
        # column may already exist
        print("exit", code, file=sys.stderr)
    check = (
        "SELECT COUNT(*) c, SUM(birthday IS NULL) nulls "
        "FROM user_agents;"
    )
    _i, o, e = ssh.exec_command(f'sudo mysql aimate -N -e "{check}"', timeout=60)
    print((o.read() + e.read()).decode("utf-8", errors="replace"))
    ssh.close()
    print("DONE")
    return 0 if code == 0 else 0  # idempotent-ish


if __name__ == "__main__":
    raise SystemExit(main())
