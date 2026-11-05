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
    "sudo journalctl -u aimate-chat -n 150 --no-pager",
    "sudo journalctl -u aimate-chat -n 300 --no-pager | grep -iE 'ERROR|Exception|groups|Group' | tail -50 || true",
]
for c in cmds:
    print(">>>", c)
    _, stdout, stderr = ssh.exec_command(c, timeout=60)
    print(stdout.read().decode("utf-8", "replace")[-5000:])
    print()
ssh.close()
