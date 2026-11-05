from pathlib import Path
import paramiko, sys, json
ROOT = Path(r"f:/dev/proj2/aimate")
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get
d=load_merged()
ssh=paramiko.SSHClient(); ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d,"DEPLOY_SSH_USER",default="ubuntu"), password=env_get(d,"DEPLOY_SSH_PASSWORD"), timeout=30, allow_agent=False, look_for_keys=False)
cmd=r'''
python3 - <<'PY'
import json, subprocess, urllib.request
from pathlib import Path
s={}
for line in Path('/home/ubuntu/java/soul/aimatex-soul.env').read_text().splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1); s[k.strip()]=v.strip()
c={}
for line in Path('/home/ubuntu/java/chat/aimate.env').read_text().splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1); c[k.strip()]=v.strip()
# get real name from chat
p=subprocess.run(['mysql','-N','-B','-h'+c.get('DB_HOST','127.0.0.1'),'-P'+c.get('DB_PORT','3306'),
 '-u'+c.get('DB_USERNAME','aimate'),'-p'+c.get('DB_PASSWORD',''),c.get('DB_NAME','aimate')],
 input='SELECT name,soul_access_token FROM user_agents WHERE id=9;', capture_output=True, text=True)
name, token = p.stdout.strip().split('\t')
print('name=', repr(name))
body=json.dumps({
  'accountSub':'2','seatId':9,'externalAgentKey':'hire-112-716145d6',
  'name': name, 'issueToken': True
}, ensure_ascii=False).encode('utf-8')
req=urllib.request.Request('http://127.0.0.1:8890/api/v1/platform/souls/create-user', data=body, method='POST',
  headers={'Content-Type':'application/json; charset=utf-8','X-Soul-Platform-Key':s['SOUL_PLATFORM_API_KEY']})
try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        created=json.loads(resp.read().decode())
    print('create-user with CN name OK', created.get('tokenIssued'), len(created.get('accessToken') or ''))
    # write new token back so chat stays valid
    tok=created['accessToken']
    tok_sql=tok.replace('\\','\\\\').replace("'","''")
    subprocess.run(['mysql','-N','-B','-h'+c.get('DB_HOST','127.0.0.1'),'-P'+c.get('DB_PORT','3306'),
     '-u'+c.get('DB_USERNAME','aimate'),'-p'+c.get('DB_PASSWORD',''),c.get('DB_NAME','aimate')],
     input=f"UPDATE user_agents SET soul_access_token='{tok_sql}' WHERE id=9;", check=True, capture_output=True, text=True)
except Exception as e:
    print('FAIL', e)
    if hasattr(e,'read'): print(e.read().decode()[:400])

# verify memory still readable with current token from DB
p=subprocess.run(['mysql','-N','-B','-h'+c.get('DB_HOST','127.0.0.1'),'-P'+c.get('DB_PORT','3306'),
 '-u'+c.get('DB_USERNAME','aimate'),'-p'+c.get('DB_PASSWORD',''),c.get('DB_NAME','aimate')],
 input='SELECT soul_access_token FROM user_agents WHERE id=9;', capture_output=True, text=True)
token=p.stdout.strip()
req=urllib.request.Request('http://127.0.0.1:8890/api/v1/soul/memories', headers={'Authorization':'Bearer '+token})
with urllib.request.urlopen(req, timeout=20) as resp:
    data=json.loads(resp.read().decode())
print('memories', data.get('total'), [(i['id'], i.get('pinned'), i.get('content')) for i in data.get('items',[])])

# chat health
import urllib.request as u
print('health', u.urlopen('http://127.0.0.1:9299/api/health', timeout=10).read().decode()[:120])
PY
'''
_,o,e=ssh.exec_command(cmd, timeout=60)
print(o.read().decode('utf-8','replace'))
print(e.read().decode('utf-8','replace')[-400:])
ssh.close()