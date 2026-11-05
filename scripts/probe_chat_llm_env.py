#!/usr/bin/env python3
"""Probe chat prod LLM env keys (masked) and system_models rows."""
from __future__ import annotations

import re
import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get  # noqa: E402


def mask(v: str) -> str:
    v = (v or "").strip()
    if not v:
        return "(empty)"
    if len(v) <= 8:
        return v[:2] + "***"
    return v[:4] + "***" + v[-4:] + f" len={len(v)}"


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
    "grep -E '^(PRIMARY_LLM_|FALLBACK_LLM_|SPRING_PROFILES)' /home/ubuntu/java/chat/aimate.env | sed 's/=.*/=***/' || true",
    "python3 - <<'PY'\n"
    "from pathlib import Path\n"
    "p=Path('/home/ubuntu/java/chat/aimate.env')\n"
    "keys=['PRIMARY_LLM_BASE_URL','PRIMARY_LLM_API_KEY','PRIMARY_LLM_MODEL',"
    "'FALLBACK_LLM_BASE_URL','FALLBACK_LLM_API_KEY','FALLBACK_LLM_MODEL']\n"
    "data={}\n"
    "for line in p.read_text(encoding='utf-8',errors='replace').splitlines():\n"
    "  line=line.strip()\n"
    "  if not line or line.startswith('#') or '=' not in line: continue\n"
    "  k,v=line.split('=',1); data[k.strip()]=v.strip()\n"
    "def mask(v):\n"
    "  if not v: return '(empty)'\n"
    "  if 'placeholder' in v.lower(): return 'PLACEHOLDER:'+v\n"
    "  if len(v)<=8: return v[:2]+'***'\n"
    "  return v[:6]+'***'+v[-4:]+f' len={len(v)}'\n"
    "for k in keys:\n"
    "  print(k+'='+mask(data.get(k,'')))\n"
    "PY",
    # system models via mysql if available
    "sudo mysql -N -e \"SELECT id,display_name,model_id,enabled,LEFT(api_key,6),LENGTH(api_key) FROM aimate.system_models ORDER BY id LIMIT 20;\" 2>/dev/null || "
    "mysql -N -e \"SELECT id,display_name,model_id,enabled,LEFT(api_key,6),LENGTH(api_key) FROM aimate.system_models ORDER BY id LIMIT 20;\" 2>/dev/null || true",
]
for c in cmds:
    print(">>>", c[:100])
    _, stdout, stderr = ssh.exec_command(c, timeout=60)
    print(stdout.read().decode("utf-8", "replace"))
    e = stderr.read().decode("utf-8", "replace")
    if e.strip():
        print("ERR", e[-500:])
ssh.close()
