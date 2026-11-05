from pathlib import Path
import paramiko, sys
ROOT = Path(r"f:/dev/proj2/aimate")
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get
d=load_merged()
ssh=paramiko.SSHClient(); ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d,"DEPLOY_SSH_USER",default="ubuntu"), password=env_get(d,"DEPLOY_SSH_PASSWORD"), timeout=30, allow_agent=False, look_for_keys=False)
sql='''
SELECT id,name,status,kind,expires_at,TIMESTAMPDIFF(HOUR,NOW(),expires_at) hours_left
FROM user_agents ORDER BY id;
SELECT gm.group_id,g.title,gm.user_agent_id,ua.name,ua.status,ua.expires_at
FROM group_members gm
JOIN chat_groups g ON g.id=gm.group_id
LEFT JOIN user_agents ua ON ua.id=gm.user_agent_id
WHERE gm.member_type='AGENT'
ORDER BY gm.group_id, gm.user_agent_id;
'''
sftp=ssh.open_sftp()
with sftp.file('/tmp/ua_all.sql','w') as f: f.write(sql)
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
p=subprocess.run(['mysql','-t','-h'+env.get('DB_HOST','127.0.0.1'),'-P'+env.get('DB_PORT','3306'),
 '-u'+env.get('DB_USERNAME','aimate'),'-p'+env.get('DB_PASSWORD',''),env.get('DB_NAME','aimate')],
 input=Path('/tmp/ua_all.sql').read_text(), capture_output=True, text=True)
print(p.stdout)
PY
'''
_,o,_=ssh.exec_command(cmd, timeout=40)
print(o.read().decode('utf-8','replace'))
ssh.close()