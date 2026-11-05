from deploy_chat_prod import HOST, load_merged, env_get
import paramiko
d = load_merged()
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d,"DEPLOY_SSH_USER",default="ubuntu"), password=env_get(d,"DEPLOY_SSH_PASSWORD"), timeout=30, allow_agent=False, look_for_keys=False)

def q(sql):
    _, o, e = ssh.exec_command("sudo mysql aimate -N -e " + repr(sql), timeout=60)
    print(o.read().decode("utf-8","replace"))
    err=e.read().decode("utf-8","replace").strip()
    if err: print("ERR", err[:300])

print("=== AGENTS ===")
q("SELECT id,name,status,execution_mode,IFNULL(daily_points_limit,'NULL'),default_system_model_id FROM user_agents WHERE name IN ('MAX','Orion','Aria','Workbuddy') ORDER BY name;")
print("=== GROUP MEMBERS ===")
q("SELECT gm.id,gm.group_id,ua.id,ua.name,IFNULL(gm.agent_reply_mode,'NULL'),gm.others_may_mention,IFNULL(gm.muted_until,'NULL'),ua.status FROM group_members gm JOIN user_agents ua ON ua.id=gm.user_agent_id WHERE gm.member_type='AGENT' AND gm.group_id IN (SELECT DISTINCT group_id FROM group_messages WHERE id BETWEEN 420 AND 430) ORDER BY ua.name;")
print("=== DAILY POINTS ===")
q("SELECT adp.id,adp.agent_id,ua.name,adp.day,adp.points_spent,adp.version FROM agent_daily_points adp JOIN user_agents ua ON ua.id=adp.agent_id WHERE adp.day=CURDATE() AND ua.name IN ('MAX','Orion','Aria','Workbuddy');")
print("=== AGENT 4 ===")
q("SELECT id,name,status FROM user_agents WHERE id=4;")
ssh.close()
