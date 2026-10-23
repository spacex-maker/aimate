#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, REMOTE_JAVA, load_merged, env_get  # noqa: E402

LOCAL = ROOT / "src" / "main" / "resources" / "db" / "migration_official_knowledge.sql"
REMOTE = f"{REMOTE_JAVA}/migration_official_knowledge.sql"


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
    sftp = ssh.open_sftp()
    sftp.put(str(LOCAL), REMOTE)
    sftp.close()
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
sql = Path("{REMOTE}").read_text()
p = subprocess.run(cmd, input=sql, capture_output=True, text=True)
print("stdout:", p.stdout)
print("stderr:", p.stderr)
print("exit:", p.returncode)
check = '''
SHOW TABLES LIKE 'kb_%';
SHOW TABLES LIKE 'agent_catalog_knowledge';
SHOW TABLES LIKE 'user_agent_knowledge';
SELECT COUNT(*) AS cats FROM kb_categories;
SELECT COUNT(*) AS kbs FROM kb_knowledge_bases;
SELECT COUNT(*) AS links FROM agent_catalog_knowledge;
'''
p2 = subprocess.run(cmd, input=check, capture_output=True, text=True)
print(p2.stdout)
print(p2.stderr)
"""
    _, o, e = ssh.exec_command("python3 -", timeout=60)
    o.channel.send(script.encode())
    o.channel.shutdown_write()
    print(o.read().decode())
    err = e.read().decode()
    if err:
        print(err, file=sys.stderr)
    # restart so bootstrap can seed if tables were empty at previous boot
    _, o2, _ = ssh.exec_command("sudo systemctl restart aimate-chat", timeout=60)
    o2.channel.recv_exit_status()
    import time
    time.sleep(16)
    _, o3, _ = ssh.exec_command(
        "sudo systemctl is-active aimate-chat; "
        "python3 - <<'PY'\n"
        "from pathlib import Path\n"
        "import subprocess\n"
        f"env={{}}\n"
        f"for line in Path('{REMOTE_JAVA}/aimate.env').read_text().splitlines():\n"
        "    line=line.strip()\n"
        "    if not line or line.startswith('#') or '=' not in line: continue\n"
        "    k,v=line.split('=',1); env[k.strip()]=v.strip()\n"
        "cmd=['mysql','-h'+env.get('DB_HOST','127.0.0.1'),'-P'+env.get('DB_PORT','3306'),"
        "'-u'+env.get('DB_USERNAME','aimate'),'-p'+env.get('DB_PASSWORD',''),env.get('DB_NAME','aimate')]\n"
        "sql='SELECT COUNT(*) cats FROM kb_categories; SELECT COUNT(*) kbs FROM kb_knowledge_bases; "
        "SELECT COUNT(*) links FROM agent_catalog_knowledge;'\n"
        "p=subprocess.run(cmd,input=sql,capture_output=True,text=True)\n"
        "print(p.stdout or p.stderr)\n"
        "PY",
        timeout=60,
    )
    print(o3.read().decode())
    ssh.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
