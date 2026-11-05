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
curl -sS -D - -o /tmp/cu2.out -X POST http://127.0.0.1:8890/api/v1/platform/souls/create-user \
  -H 'Content-Type: application/json' \
  -H "X-Soul-Platform-Key: $PK" \
  -d '{"accountSub":"2","seatId":9,"externalAgentKey":"hire-112-716145d6","name":"Workbuddy","issueToken":true}'
echo
echo BODY:; cat /tmp/cu2.out; echo
# also check security config loaded
jar tf /home/ubuntu/java/soul/aimatex-soul.jar | grep -E 'SecurityConfig|PlatformSoul' | head
'''
_,o,e=ssh.exec_command(cmd, timeout=60)
print(o.read().decode('utf-8','replace'))
print(e.read().decode('utf-8','replace')[-800:])
ssh.close()