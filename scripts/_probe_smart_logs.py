import paramiko, sys
sys.path.insert(0, "scripts")
from deploy_chat_prod import HOST, load_merged, env_get
d = load_merged()
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d, "DEPLOY_SSH_USER", default="ubuntu"), password=env_get(d, "DEPLOY_SSH_PASSWORD"), timeout=30, allow_agent=False, look_for_keys=False)
cmd = r'''bash -lc 'set -a; source /home/ubuntu/java/chat/aimate.env; set +a; mysql -h127.0.0.1 -u"$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" -N -e "SELECT id, name, execution_mode, default_system_model_id, daily_points_limit FROM user_agents WHERE name IN (\"Orion\",\"Aria\",\"Workbuddy\",\"MAX\"); SELECT agent_id, day, points_spent FROM agent_daily_points WHERE day=CURDATE() ORDER BY id DESC LIMIT 20;"' '''
stdin, stdout, stderr = ssh.exec_command(cmd, timeout=60)
print(stdout.read().decode("utf-8", "replace"))
# recent logs
cmd2 = "sudo journalctl -u aimate-chat --since '20 min ago' --no-pager 2>/dev/null | grep -E 'SMART vote|SMART skip|GroupAgent' | tail -40"
stdin, stdout, stderr = ssh.exec_command(cmd2, timeout=60)
print("LOGS:", stdout.read().decode("utf-8", "replace"))
ssh.close()
