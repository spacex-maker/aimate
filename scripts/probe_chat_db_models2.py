#!/usr/bin/env python3
from pathlib import Path
import sys
import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get  # noqa: E402

REMOTE_PY = r"""
from pathlib import Path
import subprocess
env={}
for line in Path('/home/ubuntu/java/chat/aimate.env').read_text().splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1); env[k.strip()]=v.strip()
sql = (
    "SELECT id, display_name, model_id, enabled, api_key_config_key FROM system_models ORDER BY id LIMIT 20;\n"
    "SELECT id, name, kind, default_system_model_id, status FROM user_agents ORDER BY id DESC LIMIT 15;\n"
)
p=subprocess.run(
    ['mysql','-h'+env.get('DB_HOST','127.0.0.1'),'-P'+env.get('DB_PORT','3306'),
     '-u'+env.get('DB_USERNAME','aimate'),'-p'+env.get('DB_PASSWORD',''),env.get('DB_NAME','aimate')],
    input=sql, capture_output=True, text=True)
print(p.stdout)
if p.returncode!=0:
    print('ERR', p.stderr[-400:])
"""

d = load_merged()
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(
    HOST,
    username=env_get(d, "DEPLOY_SSH_USER", default="ubuntu"),
    password=env_get(d, "DEPLOY_SSH_PASSWORD"),
    timeout=30,
)
sftp = ssh.open_sftp()
with sftp.file("/tmp/probe_models.py", "w") as f:
    f.write(REMOTE_PY)
sftp.close()
_, stdout, stderr = ssh.exec_command("python3 /tmp/probe_models.py", timeout=60)
print(stdout.read().decode("utf-8", "replace"))
print(stderr.read().decode("utf-8", "replace")[-500:])
ssh.close()
