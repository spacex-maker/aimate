import paramiko, sys
sys.path.insert(0, "scripts")
from deploy_chat_prod import HOST, load_merged, env_get, REMOTE_JAR
d = load_merged()
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d, "DEPLOY_SSH_USER", default="ubuntu"), password=env_get(d, "DEPLOY_SSH_PASSWORD"), timeout=30, allow_agent=False, look_for_keys=False)
# check if restriction string still in GroupChatService
cmd = "sudo unzip -p " + REMOTE_JAR + " BOOT-INF/classes/com/openforge/aimate/group/GroupChatService.class 2>/dev/null | strings | grep -E 'LOCAL_BRIDGE|仅支持|仅 @|MENTION_ONLY' | head -40"
stdin, stdout, stderr = ssh.exec_command(cmd, timeout=60)
print("ChatService:", stdout.read().decode("utf-8", "replace"))
# which group has recent msgs 384
cmd2 = r'''bash -lc 'set -a; source /home/ubuntu/java/chat/aimate.env; set +a; mysql -h127.0.0.1 -u"$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" -N -e "SELECT m.id, m.group_id, m.content, g.title FROM group_messages m JOIN chat_groups g ON g.id=m.group_id WHERE m.id IN (382,384,385) \\G; SELECT gm.id, gm.group_id, ua.name, gm.agent_reply_mode FROM group_members gm JOIN user_agents ua ON ua.id=gm.user_agent_id WHERE gm.group_id IN (SELECT group_id FROM group_messages WHERE id=384) AND gm.member_type=\"AGENT\";"' '''
stdin, stdout, stderr = ssh.exec_command(cmd2, timeout=60)
print(stdout.read().decode("utf-8", "replace"))
print(stderr.read().decode("utf-8", "replace")[:300])
ssh.close()
