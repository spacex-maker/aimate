from pathlib import Path
import paramiko, sys, time
ROOT = Path(r"f:/dev/proj2/aimate")
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get
d=load_merged()
ssh=paramiko.SSHClient(); ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d,"DEPLOY_SSH_USER",default="ubuntu"), password=env_get(d,"DEPLOY_SSH_PASSWORD"), timeout=30, allow_agent=False, look_for_keys=False)
sql='''
SELECT id,name,status,expires_at,kind,
  CASE WHEN expires_at IS NOT NULL AND expires_at < NOW() THEN 'PAST' ELSE 'OK' END expiry
FROM user_agents
WHERE status='ACTIVE' AND kind<>'SECRETARY'
ORDER BY id;
UPDATE user_agents
SET status='EXPIRED'
WHERE status='ACTIVE'
  AND kind<>'SECRETARY'
  AND expires_at IS NOT NULL
  AND expires_at < NOW();
SELECT ROW_COUNT() AS marked;
SELECT id,name,status,expires_at FROM user_agents WHERE status='EXPIRED' ORDER BY id DESC LIMIT 20;
'''
sftp=ssh.open_sftp()
with sftp.file('/tmp/expire_agents.sql','w') as f: f.write(sql)
sftp.close()
cmd='''
python3 - <<'PY'
from pathlib import Path
import subprocess
env={}
for line in Path('/home/ubuntu/java/chat/aimate.env').read_text().splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1); env[k.strip()]=v.strip()
sql=Path('/tmp/expire_agents.sql').read_text()
p=subprocess.run(['mysql','-t','-h'+env.get('DB_HOST','127.0.0.1'),'-P'+env.get('DB_PORT','3306'),
 '-u'+env.get('DB_USERNAME','aimate'),'-p'+env.get('DB_PASSWORD',''),env.get('DB_NAME','aimate')],
 input=sql, capture_output=True, text=True)
print(p.stdout)
if p.returncode: print(p.stderr[-500:]); raise SystemExit(p.returncode)
PY
'''
_,o,e=ssh.exec_command(cmd, timeout=60)
print(o.read().decode('utf-8','replace'))
print(e.read().decode('utf-8','replace')[-300:])
ssh.close()