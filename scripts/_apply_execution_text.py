# -*- coding: utf-8 -*-
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, env_get, load_merged  # noqa: E402


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
    stmts = [
        "ALTER TABLE local_bridge_tasks ADD COLUMN execution_text MEDIUMTEXT NULL",
        "ALTER TABLE group_messages ADD COLUMN execution_text MEDIUMTEXT NULL",
    ]
    for stmt in stmts:
        cmd = f'sudo mysql aimate -e "{stmt}"'
        _, o, e = ssh.exec_command(cmd, timeout=60)
        code = o.channel.recv_exit_status()
        out = (o.read() + e.read()).decode("utf-8", "replace").strip()
        print(f"[{code}] {stmt}")
        if out:
            print(out)
    _, o2, _ = ssh.exec_command(
        "sudo mysql aimate -N -e "
        "\"SHOW COLUMNS FROM local_bridge_tasks LIKE 'execution_text'; "
        "SHOW COLUMNS FROM group_messages LIKE 'execution_text'\"",
        timeout=30,
    )
    print(o2.read().decode("utf-8", "replace"))
    ssh.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
