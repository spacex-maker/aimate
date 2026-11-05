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
    "sudo grep -E 'groups|POST' /var/log/nginx/access.log | tail -40",
    "sudo grep -E '09:32' /var/log/nginx/access.log | grep groups | tail -20",
]
for c in cmds:
    print(">>>", c)
    _, o, e = ssh.exec_command(c, timeout=60)
    print(o.read().decode("utf-8", "replace")[-3000:])
    err = e.read().decode()
    if err.strip():
        print("ERR", err[-400:])
ssh.close()
