#!/usr/bin/env python3
from __future__ import annotations
import sys
from pathlib import Path
import paramiko
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, REMOTE_JAVA, load_merged, env_get
SQL_FILE = ROOT / "src" / "main" / "resources" / "db" / "migration_local_bridge_stream_message.sql"

def main() -> int:
    sql = SQL_FILE.read_text(encoding="utf-8")
    d = load_merged()
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=env_get(d, "DEPLOY_SSH_USER", default="ubuntu"),
                password=env_get(d, "DEPLOY_SSH_PASSWORD"), timeout=30,
                allow_agent=False, look_for_keys=False)
    sftp = ssh.open_sftp()
    remote_sql = f"{REMOTE_JAVA}/migration_local_bridge_stream_message.sql"
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
    k,v = line.split('=',1); env[k.strip()] = v.strip()
sql = Path({remote_sql!r}).read_text()
cmd = ['mysql','-h'+env.get('DB_HOST','127.0.0.1'),'-P'+env.get('DB_PORT','3306'),
       '-u'+env.get('DB_USERNAME','aimate'),'-p'+env.get('DB_PASSWORD',''),
       env.get('DB_NAME','aimate'),'-e', sql]
p = subprocess.run(cmd, capture_output=True, text=True)
print(p.stdout); print(p.stderr); print('RC', p.returncode)
"""
    _, o, e = ssh.exec_command("python3 -", timeout=60)
    o.channel.send(remote.encode()); o.channel.shutdown_write()
    print(o.read().decode()); err=e.read().decode()
    if err: print(err, file=sys.stderr)
    ssh.close(); return 0
if __name__ == "__main__":
    raise SystemExit(main())