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
    "sudo journalctl -u aimate-chat --since '2 hours ago' --no-pager | grep -iE 'GroupAgent|local.?bridge|AGENT_STATUS|dispatch|groups/' | tail -80 || true",
    "sudo journalctl -u aimate-chat --since '2 hours ago' --no-pager | grep -iE 'ERROR|Exception|WARN.*[Gg]roup|Illegal' | tail -60 || true",
    "sudo ss -lntp | grep -E '9299|13339' || true",
    "curl -sS http://127.0.0.1:9299/api/health || true",
]
for c in cmds:
    print(">>>", c)
    _, stdout, stderr = ssh.exec_command(c, timeout=90)
    print(stdout.read().decode("utf-8", "replace")[-8000:])
    print(stderr.read().decode("utf-8", "replace")[-1000:])
    print()
ssh.close()
