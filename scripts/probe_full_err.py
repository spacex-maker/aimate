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
_, o, _ = ssh.exec_command(
    "sudo journalctl -u aimate-chat --since '09:32:34' --until '09:32:36' --no-pager | "
    "grep -iE 'ERROR|WARN|Exception|Unauthorized|Caused|message|groups|COS|Sql' | sed -n '1,80p'",
    timeout=60,
)
sys.stdout.buffer.write(o.read())
ssh.close()
