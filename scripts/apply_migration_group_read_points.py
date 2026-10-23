#!/usr/bin/env python3
"""Upload and apply migration_group_read_points.sql on chat prod."""
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, REMOTE_JAVA, load_merged, env_get  # noqa: E402

LOCAL = ROOT / "src" / "main" / "resources" / "db" / "migration_group_read_points.sql"
REMOTE = f"{REMOTE_JAVA}/migration_group_read_points.sql"


def main() -> int:
    if not LOCAL.exists():
        print("missing", LOCAL, file=sys.stderr)
        return 1
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
    sftp = ssh.open_sftp()
    sftp.put(str(LOCAL), REMOTE)
    sftp.close()
    remote_py = f"""
from pathlib import Path
import subprocess
env = {{}}
for line in Path("{REMOTE_JAVA}/aimate.env").read_text().splitlines():
    line = line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    k, v = line.split("=", 1)
    env[k.strip()] = v.strip()
sql = Path("{REMOTE}").read_text()
cmd = [
    "mysql",
    "-h" + env.get("DB_HOST", "127.0.0.1"),
    "-P" + env.get("DB_PORT", "3306"),
    "-u" + env.get("DB_USERNAME", "aimate"),
    "-p" + env.get("DB_PASSWORD", ""),
    env.get("DB_NAME", "aimate"),
]
p = subprocess.run(cmd, input=sql, capture_output=True, text=True)
print("stdout:", p.stdout)
print("stderr:", p.stderr)
print("exit:", p.returncode)
p2 = subprocess.run(
    cmd + ["-e", "SHOW COLUMNS FROM group_message_reads LIKE 'points_spent';"],
    capture_output=True,
    text=True,
)
print(p2.stdout or p2.stderr)
"""
    _, o, e = ssh.exec_command("python3 -", timeout=60)
    o.channel.send(remote_py.encode())
    o.channel.shutdown_write()
    print(o.read().decode())
    err = e.read().decode()
    if err:
        print(err, file=sys.stderr)
    ssh.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
