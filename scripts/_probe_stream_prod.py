# -*- coding: utf-8 -*-
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, env_get, load_merged  # noqa: E402


def run(ssh, cmd: str) -> str:
    _, o, e = ssh.exec_command(cmd, timeout=60)
    o.channel.recv_exit_status()
    return (o.read() + e.read()).decode("utf-8", "replace")


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
    print("=== tasks")
    print(
        run(
            ssh,
            "sudo mysql aimate -e "
            "\"SELECT id, public_id, status, "
            "CHAR_LENGTH(thinking_text) th_len, CHAR_LENGTH(execution_text) ex_len, "
            "LEFT(IFNULL(execution_text,''),100) ex, "
            "LEFT(IFNULL(result_text,''),60) r "
            "FROM local_bridge_tasks ORDER BY id DESC LIMIT 6\"",
        )
    )
    print("=== messages")
    print(
        run(
            ssh,
            "sudo mysql aimate -e "
            "\"SELECT id, "
            "CHAR_LENGTH(thinking_text) th_len, CHAR_LENGTH(execution_text) ex_len, "
            "LEFT(IFNULL(thinking_text,''),80) th, "
            "LEFT(IFNULL(execution_text,''),100) ex, "
            "LEFT(IFNULL(content,''),50) c "
            "FROM group_messages WHERE sender_type='AGENT' ORDER BY id DESC LIMIT 8\"",
        )
    )
    print("=== fe assets")
    print(run(ssh, "ls -la /home/ubuntu/web/chat/assets/index-*.js"))
    print("=== jar / unit")
    print(run(ssh, "sudo systemctl show aimate-chat -p FragmentPath -p ExecStart -p ActiveState"))
    print(run(ssh, "sudo systemctl cat aimate-chat | head -50"))
    # check if jar contains distinctTrace
    print("=== jar class check")
    print(
        run(
            ssh,
            "JAR=$(sudo systemctl show aimate-chat -p ExecStart --value | tr ' ' '\\n' | grep -E '\\.jar$' | head -1); "
            "echo JAR=$JAR; "
            "sudo jar tf \"$JAR\" 2>/dev/null | grep LocalBridgeService | head -3; "
            "sudo unzip -p \"$JAR\" BOOT-INF/classes/com/openforge/aimate/localbridge/LocalBridgeService.class 2>/dev/null | strings | grep -E 'distinctTrace|execution_text|本机' | head -20",
        )
    )
    ssh.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
