import paramiko, sys
sys.path.insert(0, "scripts")
from deploy_chat_prod import HOST, load_merged, env_get
d = load_merged()
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d, "DEPLOY_SSH_USER", default="ubuntu"), password=env_get(d, "DEPLOY_SSH_PASSWORD"), timeout=30, allow_agent=False, look_for_keys=False)
cmd2 = "sudo journalctl -u aimate-chat --since '30 min ago' --no-pager 2>/dev/null | grep -aE 'SMART vote|SMART skip|SMART vote path|GroupAgent' | tail -50"
stdin, stdout, stderr = ssh.exec_command(cmd2, timeout=60)
data = stdout.read().decode("utf-8", "replace")
sys.stdout.buffer.write(data.encode("utf-8", "replace"))
ssh.close()
