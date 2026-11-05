import paramiko, sys
sys.path.insert(0, "scripts")
from deploy_chat_prod import HOST, load_merged, env_get, REMOTE_JAR
d = load_merged()
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d, "DEPLOY_SSH_USER", default="ubuntu"), password=env_get(d, "DEPLOY_SSH_PASSWORD"), timeout=30, allow_agent=False, look_for_keys=False)
cmd = "sudo unzip -p " + REMOTE_JAR + " BOOT-INF/classes/com/openforge/aimate/group/GroupAgentDispatcher.class 2>/dev/null | strings | grep -E 'LOCAL_BRIDGE|ben ji|SMART vote|effective' | head -40"
stdin, stdout, stderr = ssh.exec_command(cmd, timeout=60)
print("CLASS:", stdout.read().decode("utf-8", "replace"))
cmd2 = r'''bash -lc 'set -a; source /home/ubuntu/java/chat/aimate.env; set +a; mysql -h127.0.0.1 -u"$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" -N -e "SELECT gm.id, ua.name, gm.agent_reply_mode, ua.execution_mode FROM group_members gm JOIN user_agents ua ON ua.id=gm.user_agent_id WHERE gm.member_type=\"AGENT\" AND (ua.name LIKE \"%Orion%\" OR ua.name LIKE \"%Cursor%\" OR ua.name LIKE \"%Aria%\" OR ua.name LIKE \"%Work%\") LIMIT 30;"' '''
stdin, stdout, stderr = ssh.exec_command(cmd2, timeout=60)
print("DB:", stdout.read().decode("utf-8", "replace"))
print("E:", stderr.read().decode("utf-8", "replace")[:500])
ssh.close()
