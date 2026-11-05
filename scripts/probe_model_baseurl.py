#!/usr/bin/env python3
from pathlib import Path
import sys
import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get  # noqa: E402

REMOTE = """
from pathlib import Path
import subprocess
env={}
for line in Path('/home/ubuntu/java/chat/aimate.env').read_text().splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1); env[k.strip()]=v.strip()
sql='SELECT id, display_name, model_id, enabled, provider, base_url, api_key_config_key, sort_order FROM system_models WHERE enabled=1 ORDER BY sort_order, id;'
p=subprocess.run(['mysql','-h'+env.get('DB_HOST','127.0.0.1'),'-P'+env.get('DB_PORT','3306'),'-u'+env.get('DB_USERNAME','aimate'),'-p'+env.get('DB_PASSWORD',''),env.get('DB_NAME','aimate')], input=sql, capture_output=True, text=True)
print(p.stdout)
print(p.stderr[-200:] if p.returncode else '')
"""

d = load_merged()
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d, "DEPLOY_SSH_USER", default="ubuntu"), password=env_get(d, "DEPLOY_SSH_PASSWORD"), timeout=30)
sftp = ssh.open_sftp()
with sftp.file("/tmp/probe_baseurl.py", "w") as f:
    f.write(REMOTE)
sftp.close()
_, o, e = ssh.exec_command("python3 /tmp/probe_baseurl.py", timeout=60)
print(o.read().decode())
print(e.read().decode()[-300:])
ssh.close()
