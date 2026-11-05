from pathlib import Path
import sys
import paramiko
ROOT = Path(r"f:/dev/proj2/aimate")
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get
d = load_merged()
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d, "DEPLOY_SSH_USER", default="ubuntu"), password=env_get(d, "DEPLOY_SSH_PASSWORD"), timeout=30)
sql = '''
SELECT id, name, status, execution_mode, soul_agent_id, soul_key_id,
  CASE WHEN soul_access_token IS NULL OR soul_access_token='' THEN 'NO' ELSE 'YES' END has_token,
  tool_memory_enabled, catalog_id
FROM user_agents WHERE id IN (3,9,1,2);
'''
sftp=ssh.open_sftp()
with sftp.file('/tmp/soul_bind.sql','w') as f: f.write(sql)
sftp.close()
_,o,e=ssh.exec_command("""
python3 - <<'PY'
from pathlib import Path
import subprocess
env={}
for line in Path('/home/ubuntu/java/chat/aimate.env').read_text().splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1); env[k.strip()]=v.strip()
sql=Path('/tmp/soul_bind.sql').read_text()
p=subprocess.run(['mysql','-t','-h'+env.get('DB_HOST','127.0.0.1'),'-P'+env.get('DB_PORT','3306'),
 '-u'+env.get('DB_USERNAME','aimate'),'-p'+env.get('DB_PASSWORD',''),env.get('DB_NAME','aimate')],
 input=sql, capture_output=True, text=True)
print(p.stdout)
if p.returncode: print(p.stderr[-500:])
PY
sudo journalctl -u aimate-chat --since '30 min ago' --no-pager | grep -iE 'Soul|memory capture|store_memory|Workbuddy|ua.?9|GroupMemory|soulBound|soul_access' | tail -40
""", timeout=60)
print(o.read().decode('utf-8','replace')[-12000:])
ssh.close()