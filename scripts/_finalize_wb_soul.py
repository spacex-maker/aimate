from pathlib import Path
import paramiko, sys, json, time
ROOT = Path(r"f:/dev/proj2/aimate")
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get
d=load_merged()
ssh=paramiko.SSHClient(); ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d,"DEPLOY_SSH_USER",default="ubuntu"), password=env_get(d,"DEPLOY_SSH_PASSWORD"), timeout=30, allow_agent=False, look_for_keys=False)

# reissue via create-user (ASCII name), update chat, delete dup mem 102, pin 101, restart chat
cmd=r'''
python3 - <<'PY'
import json, subprocess, urllib.request
from pathlib import Path

def load_env(path):
    e={}
    for line in Path(path).read_text().splitlines():
        line=line.strip()
        if not line or line.startswith('#') or '=' not in line: continue
        k,v=line.split('=',1); e[k.strip()]=v.strip()
    return e

s=load_env('/home/ubuntu/java/soul/aimatex-soul.env')
c=load_env('/home/ubuntu/java/chat/aimate.env')
pk=s['SOUL_PLATFORM_API_KEY']

# create-user reissue
body=json.dumps({
  'accountSub':'2','seatId':9,'externalAgentKey':'hire-112-716145d6',
  'name':'Workbuddy','issueToken':True
}).encode()
req=urllib.request.Request('http://127.0.0.1:8890/api/v1/platform/souls/create-user', data=body, method='POST',
  headers={'Content-Type':'application/json','X-Soul-Platform-Key':pk})
with urllib.request.urlopen(req, timeout=30) as resp:
    created=json.loads(resp.read().decode())
token=created['accessToken']
print('REISSUE', created.get('keyId'), created.get('tokenIssued'), len(token))

tok_sql=token.replace('\\','\\\\').replace("'","''")
sql=f"UPDATE user_agents SET soul_access_token='{tok_sql}' WHERE id=9;"
subprocess.run(['mysql','-N','-B','-h'+c.get('DB_HOST','127.0.0.1'),'-P'+c.get('DB_PORT','3306'),
 '-u'+c.get('DB_USERNAME','aimate'),'-p'+c.get('DB_PASSWORD',''),c.get('DB_NAME','aimate')],
 input=sql, check=True, capture_output=True, text=True)

# delete duplicate 102, pin 101
for mid, method in [(102,'DELETE'), (101,'POST')]:
    url=f'http://127.0.0.1:8890/api/v1/soul/memories/{mid}' + ('/pin' if method=='POST' else '')
    r=urllib.request.Request(url, method=method, headers={'Authorization':'Bearer '+token},
      data=(b'{}' if method=='POST' else None))
    if method=='POST':
        r.add_header('Content-Type','application/json')
    try:
        with urllib.request.urlopen(r, timeout=20) as resp:
            print(method, mid, resp.status, resp.read()[:120])
    except Exception as e:
        print(method, mid, 'err', e, getattr(e,'read',lambda:b'')()[:200])

# list
r=urllib.request.Request('http://127.0.0.1:8890/api/v1/soul/memories', headers={'Authorization':'Bearer '+token})
with urllib.request.urlopen(r, timeout=20) as resp:
    data=json.loads(resp.read().decode())
print('COUNT', data.get('total'), [(i.get('id'), i.get('pinned'), i.get('kind')) for i in data.get('items',[])])
print('OK')
PY
sudo systemctl restart aimate-chat
sleep 12
sudo systemctl is-active aimate-chat
curl -sS http://127.0.0.1:9299/api/health || true
'''
_,o,e=ssh.exec_command(cmd, timeout=90)
print(o.read().decode('utf-8','replace'))
print(e.read().decode('utf-8','replace')[-500:])
ssh.close()