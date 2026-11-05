#!/usr/bin/env python3
"""Probe group API presence + recent auth failures on chat prod."""
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get  # noqa: E402


def main() -> int:
    data = load_merged()
    password = env_get(data, "DEPLOY_SSH_PASSWORD")
    user = env_get(data, "DEPLOY_SSH_USER", default="ubuntu")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=user, password=password, timeout=30)

    cmds = [
        "jar tf /home/ubuntu/java/chat/aimate.jar | grep GroupController || echo NO_GROUP_CTRL",
        "jar tf /home/ubuntu/java/chat/aimate.jar | grep '/group/' | head",
        "sudo journalctl -u aimate -n 120 --no-pager | tail -80",
        "sudo mysql aimate -e \"SHOW TABLES LIKE 'chat_groups'; SHOW TABLES LIKE 'group_members';\"",
        "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:9299/api/groups",
        "curl -s http://127.0.0.1:9299/api/groups | head -c 300",
    ]
    for c in cmds:
        print(">>>", c)
        _, stdout, stderr = ssh.exec_command(c, timeout=60)
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        print(out[-3000:] if out else "(empty)")
        if err.strip():
            print("ERR:", err[-800:])
        print()
    ssh.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
