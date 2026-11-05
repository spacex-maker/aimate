from pathlib import Path
import paramiko, sys
ROOT = Path(r"f:/dev/proj2/aimate")
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get
d=load_merged()
ssh=paramiko.SSHClient(); ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d,"DEPLOY_SSH_USER",default="ubuntu"), password=env_get(d,"DEPLOY_SSH_PASSWORD"), timeout=30, allow_agent=False, look_for_keys=False)
cmd=r'''
PK=$(grep '^SOUL_PLATFORM_API_KEY=' /home/ubuntu/java/soul/aimatex-soul.env | cut -d= -f2-)
# trigger + immediate logs
curl -sS -o /tmp/cu3.out -w '%{http_code}' -X POST http://127.0.0.1:8890/api/v1/platform/souls/create-user \
  -H 'Content-Type: application/json' \
  -H "X-Soul-Platform-Key: $PK" \
  -d '{"accountSub":"2","seatId":9,"externalAgentKey":"hire-112-716145d6","name":"Workbuddy","issueToken":true}'
echo
# purchase-seat for comparison
curl -sS -o /tmp/ps.out -w 'purchase %{http_code}\n' -X POST http://127.0.0.1:8890/api/v1/platform/souls/seats/purchase \
  -H 'Content-Type: application/json' \
  -H "X-Soul-Platform-Key: $PK" \
  -d '{"accountSub":"2","period":"DAY","source":"CHAT_AGENT"}'
head -c 200 /tmp/ps.out; echo
# renew
curl -sS -o /tmp/rn.out -w 'renew %{http_code}\n' -X POST http://127.0.0.1:8890/api/v1/platform/souls/seats/renew \
  -H 'Content-Type: application/json' \
  -H "X-Soul-Platform-Key: $PK" \
  -d '{"accountSub":"2","seatId":9,"period":"DAY"}'
head -c 200 /tmp/rn.out; echo
# ensure
curl -sS -o /tmp/en.out -w 'ensure %{http_code}\n' -X POST http://127.0.0.1:8890/api/v1/platform/souls/ensure \
  -H 'Content-Type: application/json' \
  -H "X-Soul-Platform-Key: $PK" \
  -d '{"accountSub":"2","platformCode":"aimatex-chat","issueToken":true}'
head -c 200 /tmp/en.out; echo
sudo journalctl -u aimatex-soul --since '1 min ago' --no-pager | grep -iE 'create-user|Forbidden|AccessDenied|Exception|WARN|ERROR' | tail -30
'''
_,o,e=ssh.exec_command(cmd, timeout=60)
print(o.read().decode('utf-8','replace'))
print(e.read().decode('utf-8','replace')[-500:])
ssh.close()