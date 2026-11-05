#!/usr/bin/env python3
import sys
from pathlib import Path
import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get  # noqa: E402

d = load_merged()
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(
    HOST,
    username=env_get(d, "DEPLOY_SSH_USER", default="ubuntu"),
    password=env_get(d, "DEPLOY_SSH_PASSWORD"),
    timeout=30,
)
cmds = [
    "systemctl list-units --type=service --all | grep -iE 'aimate|chat' || true",
    "ls -la /home/ubuntu/java/chat/",
    "ps aux | grep java | grep -v grep",
    "ls /etc/systemd/system | grep -i aimate || true",
    "sudo journalctl -u aimate -n 100 --no-pager || true",
    "curl -sI https://chat-api.aimatex.com/actuator/health | head -15",
    "curl -s -X OPTIONS https://chat-api.aimatex.com/api/groups -H 'Origin: https://chat.aimatex.com' -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: authorization,content-type' -D - -o /dev/null | head -30",
]
for c in cmds:
    print(">>>", c)
    _, stdout, stderr = ssh.exec_command(c, timeout=60)
    print(stdout.read().decode("utf-8", "replace")[-3000:])
    err = stderr.read().decode("utf-8", "replace")
    if err.strip():
        print("ERR", err[-600:])
    print()
ssh.close()
