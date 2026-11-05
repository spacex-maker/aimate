#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get  # noqa: E402

REMOTE = r'''
python3 <<'PY'
from pathlib import Path
import subprocess
env={}
for line in Path('/home/ubuntu/java/chat/aimate.env').read_text().splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1); env[k.strip()]=v.strip()
user=env.get('DB_USERNAME','aimate')
password=env.get('DB_PASSWORD','')
db=env.get('DB_NAME','aimate')
host=env.get('DB_HOST','127.0.0.1')
port=env.get('DB_PORT','3306')
sql = """
SELECT config_key,
       LEFT(config_value,8) AS prefix,
       LENGTH(config_value) AS len,
       CASE WHEN LOWER(config_value) LIKE '%placeholder%' THEN 'PH' ELSE 'OK' END AS st
FROM system_config
WHERE config_key LIKE '%KEY%' OR config_key LIKE '%API%' OR config_key LIKE '%LLM%' OR config_key LIKE '%deepseek%'
ORDER BY config_key LIMIT 40;
SELECT id, display_name, model_id, enabled, api_key_config_key,
       LEFT(IFNULL(api_key,''),8), LENGTH(IFNULL(api_key,''))
FROM system_models ORDER BY id LIMIT 30;
"""
Path('/tmp/q.sql').write_text(sql)
cmd=['mysql', f'-h{host}', f'-P{port}', f'-u{user}', f'-p{password}', db]
p=subprocess.run(cmd, input=sql, capture_output=True, text=True)
print(p.stdout)
if p.returncode!=0:
    print('ERR', p.stderr[-500:])
PY
'''

d = load_merged()
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d, "DEPLOY_SSH_USER", default="ubuntu"),
            password=env_get(d, "DEPLOY_SSH_PASSWORD"), timeout=30)
_, stdout, stderr = ssh.exec_command(REMOTE, timeout=60)
print(stdout.read().decode("utf-8", "replace"))
err = stderr.read().decode("utf-8", "replace")
if err.strip():
    print(err[-800:])
ssh.close()
