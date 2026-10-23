#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, REMOTE_JAVA, load_merged, env_get  # noqa: E402


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
    script = f"""
from pathlib import Path
import subprocess
env = {{}}
for line in Path("{REMOTE_JAVA}/aimate.env").read_text().splitlines():
    line = line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    k, v = line.split("=", 1)
    env[k.strip()] = v.strip()
cmd = [
    "mysql",
    "-h" + env.get("DB_HOST", "127.0.0.1"),
    "-P" + env.get("DB_PORT", "3306"),
    "-u" + env.get("DB_USERNAME", "aimate"),
    "-p" + env.get("DB_PASSWORD", ""),
    env.get("DB_NAME", "aimate"),
]
sql = \"\"\"
UPDATE group_message_reads SET points_spent = 0 WHERE points_spent IS NULL;
ALTER TABLE group_message_reads MODIFY COLUMN points_spent BIGINT NOT NULL DEFAULT 0;
SHOW COLUMNS FROM group_message_reads LIKE 'points_spent';
\"\"\"
p = subprocess.run(cmd, input=sql, capture_output=True, text=True)
print(p.stdout)
print(p.stderr)
print("exit", p.returncode)
"""
    _, o, e = ssh.exec_command("python3 -", timeout=60)
    o.channel.send(script.encode())
    o.channel.shutdown_write()
    print(o.read().decode())
    err = e.read().decode()
    if err:
        print(err, file=sys.stderr)
    ssh.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
