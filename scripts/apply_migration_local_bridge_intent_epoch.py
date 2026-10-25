#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, REMOTE_JAVA, load_merged, env_get  # noqa: E402

SQL_FILE = ROOT / "src" / "main" / "resources" / "db" / "migration_local_bridge_intent_epoch.sql"


def main() -> int:
    sql = SQL_FILE.read_text(encoding="utf-8")
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
    remote_sql = f"{REMOTE_JAVA}/migration_local_bridge_intent_epoch.sql"
    with sftp.file(remote_sql, "w") as f:
        f.write(sql)
    sftp.close()
    remote = f"""
from pathlib import Path
import subprocess
env = {{}}
for line in Path({REMOTE_JAVA!r} + '/aimate.env').read_text().splitlines():
    line = line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v = line.split('=',1)
    env[k.strip()] = v.strip()
sql = Path({remote_sql!r}).read_text()
stmts = [s.strip() for s in sql.split(';') if s.strip() and not s.strip().startswith('--')]
rc = 0
for stmt in stmts:
    cmd = ['mysql','-h'+env.get('DB_HOST','127.0.0.1'),'-P'+env.get('DB_PORT','3306'),
           '-u'+env.get('DB_USERNAME','aimate'),'-p'+env.get('DB_PASSWORD',''),
           env.get('DB_NAME','aimate'),'-e', stmt]
    p = subprocess.run(cmd, capture_output=True, text=True)
    err = (p.stderr or '') + (p.stdout or '')
    if p.returncode != 0 and 'Duplicate' not in err:
        print('FAIL', stmt[:80], err)
        rc = p.returncode
    else:
        print('OK', stmt[:60].replace(chr(10),' '))
print('RC', rc)
"""
    _, o, e = ssh.exec_command("python3 -", timeout=60)
    o.channel.send(remote.encode())
    o.channel.shutdown_write()
    print(o.read().decode())
    err = e.read().decode()
    if err:
        print(err, file=sys.stderr)
    ssh.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())