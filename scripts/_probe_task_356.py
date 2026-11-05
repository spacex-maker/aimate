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
    sql = (
        "SELECT public_id, status, "
        "CHAR_LENGTH(thinking_text) th_len, CHAR_LENGTH(execution_text) ex_len, "
        "thinking_text, execution_text, LEFT(result_text,80) r "
        "FROM local_bridge_tasks WHERE public_id='lbt_bfb9b7bce635455c8c3c87d644568a30'\\G"
    )
    _, o, e = ssh.exec_command(f'sudo mysql aimate -e "{sql}"', timeout=60)
    print((o.read() + e.read()).decode("utf-8", "replace"))
    sql2 = (
        "SELECT id, CHAR_LENGTH(thinking_text) th, CHAR_LENGTH(execution_text) ex, "
        "thinking_text, execution_text, LEFT(content,60) c "
        "FROM group_messages WHERE content LIKE '%msg_id=356%'\\G"
    )
    _, o2, e2 = ssh.exec_command(f'sudo mysql aimate -e "{sql2}"', timeout=60)
    print((o2.read() + e2.read()).decode("utf-8", "replace"))
    ssh.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
