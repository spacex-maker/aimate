from pathlib import Path
import paramiko, sys
ROOT = Path(r"f:/dev/proj2/aimate")
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get
d=load_merged()
ssh=paramiko.SSHClient(); ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d,"DEPLOY_SSH_USER",default="ubuntu"), password=env_get(d,"DEPLOY_SSH_PASSWORD"), timeout=30, allow_agent=False, look_for_keys=False)
cmd=r'''
# dump mappings containing platform
sudo journalctl -u aimatex-soul --since '20 min ago' --no-pager | grep -i 'Mapped\|create-user\|platform/souls' | head -20
# use javap on PlatformSoulService to see if our reissue path exists
cd /tmp
jar xf /home/ubuntu/java/soul/aimatex-soul.jar BOOT-INF/classes/com/aimatex/soul/platform/PlatformSoulService.class
javap -c BOOT-INF/classes/com/aimatex/soul/platform/PlatformSoulService.class 2>/dev/null | grep -E '席位已绑定|externalAgentKey|tokenIssued|agentRepository.save' | head -20
# strings for Chinese error
strings BOOT-INF/classes/com/aimatex/soul/platform/PlatformSoulService.class | grep -E '席位|复用|重新' | head
'''
_,o,e=ssh.exec_command(cmd, timeout=60)
print(o.read().decode('utf-8','replace'))
print(e.read().decode('utf-8','replace')[-800:])
ssh.close()