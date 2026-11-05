from pathlib import Path
import paramiko, sys
ROOT = Path(r"f:/dev/proj2/aimate")
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get
d=load_merged()
ssh=paramiko.SSHClient(); ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d,"DEPLOY_SSH_USER",default="ubuntu"), password=env_get(d,"DEPLOY_SSH_PASSWORD"), timeout=30, allow_agent=False, look_for_keys=False)
cmd=r'''
# check deepseek key placeholder in system_models / env (no dump full key)
python3 - <<'PY'
from pathlib import Path
import subprocess
env={}
for line in Path('/home/ubuntu/java/chat/aimate.env').read_text().splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1); env[k.strip()]=v.strip()
for k in sorted(env):
    if 'DEEPSEEK' in k.upper() or 'LLM' in k.upper() or 'OPENAI' in k.upper() or 'API_KEY' in k.upper():
        v=env[k]
        print(k, 'len='+str(len(v)), 'prefix='+v[:4], 'placeholder='+str('****' in v or 'your_' in v.lower() or v in ('','changeme')))
sql="SELECT id,display_name,model_id,enabled,LEFT(api_key,4),LENGTH(api_key),CASE WHEN api_key LIKE '%****%' OR api_key LIKE '%placeholder%' OR api_key='' THEN 1 ELSE 0 END bad FROM system_models ORDER BY id LIMIT 15;"
p=subprocess.run(['mysql','-t','-h'+env.get('DB_HOST','127.0.0.1'),'-P'+env.get('DB_PORT','3306'),
 '-u'+env.get('DB_USERNAME','aimate'),'-p'+env.get('DB_PASSWORD',''),env.get('DB_NAME','aimate')],
 input=sql, capture_output=True, text=True)
print(p.stdout)
PY
'''
_,o,e=ssh.exec_command(cmd, timeout=40)
print(o.read().decode('utf-8','replace'))
ssh.close()