#!/usr/bin/env python3
"""Reissue Workbuddy Soul token from soul host (localhost) then update chat DB."""
from __future__ import annotations

import json
import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get  # noqa: E402

AGENT_ID = 9


def main() -> int:
    d = load_merged()
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(
        HOST,
        username=env_get(d, "DEPLOY_SSH_USER", default="ubuntu"),
        password=env_get(d, "DEPLOY_SSH_PASSWORD"),
        timeout=30,
        allow_agent=False,
        look_for_keys=False,
    )

    remote = r'''
python3 - <<'PY'
import json, subprocess, urllib.request
from pathlib import Path

# chat row
env={}
for line in Path('/home/ubuntu/java/chat/aimate.env').read_text().splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1); env[k.strip()]=v.strip()
sql=("SELECT u.aimatex_account_sub, ua.soul_seat_id, ua.external_agent_key, ua.name, ua.soul_key_id "
     "FROM user_agents ua JOIN users u ON u.id=ua.user_id WHERE ua.id=9;")
p=subprocess.run(['mysql','-N','-B','-h'+env.get('DB_HOST','127.0.0.1'),'-P'+env.get('DB_PORT','3306'),
 '-u'+env.get('DB_USERNAME','aimate'),'-p'+env.get('DB_PASSWORD',''),env.get('DB_NAME','aimate')],
 input=sql, capture_output=True, text=True)
row=p.stdout.strip().split('\t')
if len(row)<5:
    raise SystemExit('ua missing: '+repr(p.stdout)+p.stderr[-200:])
account_sub, seat_id, ext_key, name, old_key = row
print('ROW', account_sub, seat_id, ext_key, name, old_key)

# platform key from soul env
senv={}
for line in Path('/home/ubuntu/java/soul/aimatex-soul.env').read_text().splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1); senv[k.strip()]=v.strip()
pk=senv.get('SOUL_PLATFORM_API_KEY','')
if not pk:
    raise SystemExit('no platform key on soul host')

body=json.dumps({
  'accountSub': account_sub,
  'seatId': int(seat_id),
  'externalAgentKey': ext_key,
  'name': name,
  'issueToken': True,
}).encode()
req=urllib.request.Request(
  'http://127.0.0.1:8890/api/v1/platform/souls/create-user',
  data=body, method='POST',
  headers={'Content-Type':'application/json','X-Soul-Platform-Key':pk},
)
try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        created=json.loads(resp.read().decode())
except Exception as e:
    if hasattr(e,'read'):
        print('ERRBODY', e.read().decode('utf-8','replace')[:800])
    raise
token=created.get('accessToken') or ''
key_id=created.get('keyId') or old_key
agent_id=created.get('agentId')
print('REISSUE', key_id, agent_id, len(token), created.get('tokenIssued'))
if not token:
    raise SystemExit('empty token '+json.dumps(created)[:500])

tok_sql=token.replace('\\','\\\\').replace("'","''")
key_sql=str(key_id).replace("'","''")
set_agent=f", soul_agent_id={int(agent_id)}" if agent_id else ""
upd=(f"UPDATE user_agents SET soul_access_token='{tok_sql}', soul_key_id='{key_sql}'{set_agent} WHERE id=9; "
     "SELECT id,soul_key_id,CHAR_LENGTH(soul_access_token) FROM user_agents WHERE id=9;")
p2=subprocess.run(['mysql','-N','-B','-h'+env.get('DB_HOST','127.0.0.1'),'-P'+env.get('DB_PORT','3306'),
 '-u'+env.get('DB_USERNAME','aimate'),'-p'+env.get('DB_PASSWORD',''),env.get('DB_NAME','aimate')],
 input=upd, capture_output=True, text=True)
print('DB', p2.stdout.strip(), p2.stderr[-200:] if p2.returncode else '')

# verify me + seed memory
req2=urllib.request.Request('http://127.0.0.1:8890/api/v1/soul/me', headers={'Authorization':'Bearer '+token})
with urllib.request.urlopen(req2, timeout=20) as resp:
    me=json.loads(resp.read().decode())
print('ME', me.get('keyId') or (me.get('agent') or {}).get('keyId'), me.get('name') or (me.get('agent') or {}).get('name'))

mbody=json.dumps({
  'content': '用户自称 An，称呼时使用 An。',
  'kind': 'PROFILE',
  'importance': 0.9,
  'client': {'app':'aimatex-chat','device':'DESKTOP-19P2LKC','label':'repair'},
}).encode()
req3=urllib.request.Request('http://127.0.0.1:8890/api/v1/soul/memories', data=mbody, method='POST',
  headers={'Content-Type':'application/json','Authorization':'Bearer '+token})
with urllib.request.urlopen(req3, timeout=20) as resp:
    mem=json.loads(resp.read().decode())
print('MEM', mem.get('id'), mem.get('kind'), (mem.get('content') or '')[:40])
print('OK')
PY
'''
    _, o, e = ssh.exec_command(remote, timeout=90)
    code = o.channel.recv_exit_status()
    print(o.read().decode("utf-8", "replace"))
    err = e.read().decode("utf-8", "replace")
    if err:
        print("STDERR:", err[-1000:])
    if code != 0:
        return code
    _, o2, _ = ssh.exec_command("sudo systemctl restart aimate-chat", timeout=60)
    o2.channel.recv_exit_status()
    print("aimate-chat restarted")
    ssh.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())