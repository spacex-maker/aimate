from deploy_chat_prod import HOST, load_merged, env_get
import paramiko
d=load_merged(); ssh=paramiko.SSHClient(); ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(d,"DEPLOY_SSH_USER",default="ubuntu"), password=env_get(d,"DEPLOY_SSH_PASSWORD"), timeout=30, allow_agent=False, look_for_keys=False)
def q(sql):
    _,o,e=ssh.exec_command("sudo mysql aimate -e "+repr(sql), timeout=60)
    print(o.read().decode("utf-8","replace")); err=e.read().decode().strip();
    if err: print("ERR",err[:400])
print("=== COLS ==="); q("SHOW COLUMNS FROM agent_daily_points;")
print("=== POINTS TODAY ==="); q("SELECT * FROM agent_daily_points WHERE day=CURDATE();")
print("=== MAX msgs today ==="); q("SELECT id,LEFT(content,40),created_at FROM group_messages WHERE sender_type=\"AGENT\" AND sender_agent_id=2 AND created_at>=CURDATE() ORDER BY id DESC LIMIT 10;")
ssh.close()
