#!/usr/bin/env python3
import sys
from pathlib import Path
import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get  # noqa: E402

d = load_merged()
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(
    HOST,
    username=env_get(d, "DEPLOY_SSH_USER", default="ubuntu"),
    password=env_get(d, "DEPLOY_SSH_PASSWORD"),
    timeout=30,
)
cmds = [
    "sudo journalctl -u aimate-chat --since '30 min ago' --no-pager | grep -iE 'GroupAgent|SMART|relevance|generate failed|virtual-' | tail -80 || true",
    """python3 <<'PY'
from pathlib import Path
import subprocess
env={}
for line in Path('/home/ubuntu/java/chat/aimate.env').read_text().splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1); env[k.strip()]=v.strip()
sql='''
SELECT gm.id, gm.group_id, gm.member_type, gm.agent_reply_mode, ua.id ua_id, ua.name, ua.kind, ua.execution_mode
FROM group_members gm
LEFT JOIN user_agents ua ON ua.id = gm.user_agent_id
WHERE gm.member_type='AGENT'
ORDER BY gm.id DESC LIMIT 30;
SELECT id, LEFT(content,80), sender_type, create_time FROM group_messages ORDER BY id DESC LIMIT 15;
'''
p=subprocess.run(['mysql','-h'+env.get('DB_HOST','127.0.0.1'),'-P'+env.get('DB_PORT','3306'),
 '-u'+env.get('DB_USERNAME','aimate'),'-p'+env.get('DB_PASSWORD',''),env.get('DB_NAME','aimate')],
 input=sql, capture_output=True, text=True)
print(p.stdout)
if p.returncode: print(p.stderr[-400:])
PY""",
]
for c in cmds:
    print(">>>", c[:60])
    _, o, e = ssh.exec_command(c, timeout=90)
    print(o.read().decode("utf-8", "replace")[-9000:])
    err = e.read().decode("utf-8", "replace")
    if err.strip():
        print("ERR", err[-500:])
ssh.close()
