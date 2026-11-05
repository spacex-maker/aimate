#!/usr/bin/env python3
import sys
import time
from pathlib import Path
import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get  # noqa: E402

d = load_merged()
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(
    HOST,
    username=env_get(d, "DEPLOY_SSH_USER", default="ubuntu"),
    password=env_get(d, "DEPLOY_SSH_PASSWORD"),
    timeout=30,
)
time.sleep(10)
for cmd in [
    "sudo systemctl is-active aimate-chat",
    "curl -sS http://127.0.0.1:9299/api/health",
    "grep -E '^(PRIMARY_LLM_|FALLBACK_LLM_)' /home/ubuntu/java/chat/aimate.env | sed 's/=sk-.*/=sk-***MASKED/'",
]:
    print("===", cmd)
    _, o, e = c.exec_command(cmd, timeout=40)
    print(o.read().decode("utf-8", "replace"))
    err = e.read().decode("utf-8", "replace")
    if err.strip():
        print(err[-300:])
c.close()
