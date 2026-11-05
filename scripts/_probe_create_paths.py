from pathlib import Path
import paramiko, sys
ROOT = Path(r"f:/dev/proj2/aimate")
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get
d=load_merged()
ssh=paramiko.SSHClient(); ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d,"DEPLOY_SSH_USER",default="ubuntu"), password=env_get(d,"DEPLOY_SSH_PASSWORD"), timeout=30, allow_agent=False, look_for_keys=False)
cmd=r'''
# enable security debug briefly via curl to a wrong path vs create-user with spring boot admin? 
# Check handler mapping via beans - use jcmd or just strings on controller
cd /tmp
jar xf /home/ubuntu/java/soul/aimatex-soul.jar BOOT-INF/classes/com/aimatex/soul/platform/PlatformSoulController.class
javap -v BOOT-INF/classes/com/aimatex/soul/platform/PlatformSoulController.class 2>/dev/null | grep -E 'create-user|PostMapping|RuntimeVisibleAnnotations|ensure-user|purchase' | head -40
# Try alternate path spelling
PK=$(grep '^SOUL_PLATFORM_API_KEY=' /home/ubuntu/java/soul/aimatex-soul.env | cut -d= -f2-)
for path in create-user create_user createUser souls/create-user; do
  code=$(curl -sS -o /tmp/x.out -w '%{http_code}' -X POST "http://127.0.0.1:8890/api/v1/platform/souls/$path" \
    -H 'Content-Type: application/json' -H "X-Soul-Platform-Key: $PK" \
    -d '{"accountSub":"2","seatId":9,"externalAgentKey":"hire-112-716145d6","name":"Workbuddy","issueToken":true}')
  echo "$path -> $code $(head -c 80 /tmp/x.out)"
done
'''
_,o,e=ssh.exec_command(cmd, timeout=60)
print(o.read().decode('utf-8','replace'))
print(e.read().decode('utf-8','replace')[-400:])
ssh.close()