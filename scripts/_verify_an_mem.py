from pathlib import Path
import paramiko, sys
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
c={}
for line in Path('/home/ubuntu/java/chat/aimate.env').read_text().splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1); c[k.strip()]=v.strip()
p=subprocess.run(['mysql','-N','-B','-h'+c.get('DB_HOST','127.0.0.1'),'-P'+c.get('DB_PORT','3306'),
 '-u'+c.get('DB_USERNAME','aimate'),'-p'+c.get('DB_PASSWORD',''),c.get('DB_NAME','aimate')],
 input='SELECT soul_access_token FROM user_agents WHERE id=9;', capture_output=True, text=True)
token=p.stdout.strip()
print('tok_len', len(token))

def get(path):
    req=urllib.request.Request('http://127.0.0.1:8890/api/v1/soul'+path, headers={'Authorization':'Bearer '+token})
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode())

# list memories
try:
    print('MEMORIES', json.dumps(get('/memories'), ensure_ascii=False)[:800])
except Exception as e:
    print('list fail', e, getattr(e,'read',lambda:b'')().decode()[:400])

# post again with verbose
body=json.dumps({'content':'用户自称 An，称呼时使用 An。','kind':'PROFILE','importance':0.9,
 'client':{'app':'aimatex-chat','device':'repair'}}, ensure_ascii=False).encode()
req=urllib.request.Request('http://127.0.0.1:8890/api/v1/soul/memories', data=body, method='POST',
 headers={'Content-Type':'application/json; charset=utf-8','Authorization':'Bearer '+token})
try:
    with urllib.request.urlopen(req, timeout=20) as resp:
        raw=resp.read().decode()
        print('POST_STATUS', resp.status)
        print('POST_BODY', raw[:800])
except Exception as e:
    print('post fail', e)
    if hasattr(e,'read'): print(e.read().decode()[:500])

print('CONTEXT', json.dumps(get('/context?q=An'), ensure_ascii=False)[:800])
PY
'''
_,o,e=ssh.exec_command(cmd, timeout=60)
print(o.read().decode('utf-8','replace'))
print(e.read().decode('utf-8','replace')[-500:])
ssh.close()