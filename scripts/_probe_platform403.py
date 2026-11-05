from pathlib import Path
import paramiko, sys
ROOT = Path(r"f:/dev/proj2/aimate")
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get
d=load_merged()
ssh=paramiko.SSHClient(); ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d,"DEPLOY_SSH_USER",default="ubuntu"), password=env_get(d,"DEPLOY_SSH_PASSWORD"), timeout=30, allow_agent=False, look_for_keys=False)
cmd=r'''
# compare platform key lengths / prefix without dumping secrets
python3 - <<'PY'
from pathlib import Path
s={}
for line in Path('/home/ubuntu/java/soul/aimatex-soul.env').read_text().splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1); s[k.strip()]=v.strip()
pk=s.get('SOUL_PLATFORM_API_KEY','')
print('pk_len', len(pk), 'prefix', pk[:6] if pk else None, 'suffix', pk[-4:] if pk else None)
PY
# curl with verbose status from journal
sudo journalctl -u aimatex-soul --since '5 min ago' --no-pager | tail -40
curl -sS -o /tmp/cu.out -w 'HTTP %{http_code}\n' -X POST http://127.0.0.1:8890/api/v1/platform/souls/ensure-user \
  -H 'Content-Type: application/json' \
  -H "X-Soul-Platform-Key: $(grep '^SOUL_PLATFORM_API_KEY=' /home/ubuntu/java/soul/aimatex-soul.env | cut -d= -f2-)" \
  -d '{"accountSub":"2","displayName":"t"}'
echo BODY:; head -c 400 /tmp/cu.out; echo
'''
_,o,e=ssh.exec_command(cmd, timeout=60)
print(o.read().decode('utf-8','replace'))
print(e.read().decode('utf-8','replace')[-1500:])
ssh.close()