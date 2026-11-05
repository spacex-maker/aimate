# -*- coding: utf-8 -*-
"""Probe MAX agent reply mode / quota / SMART logs on chat prod."""
from deploy_chat_prod import HOST, load_merged, env_get
import paramiko

d = load_merged()
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(
    HOST,
    username=env_get(d, "DEPLOY_SSH_USER", default="ubuntu"),
    password=env_get(d, "DEPLOY_SSH_PASSWORD"),
    timeout=30,
    allow_agent=False,
    look_for_keys=False,
)


def run(cmd: str, timeout: int = 90) -> str:
    _, o, e = ssh.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    return (out + (("\nERR:" + err) if err.strip() else "")).strip()


sql = r"""
SET NAMES utf8mb4;
SELECT ua.id, ua.name, ua.status, ua.execution_mode, ua.daily_points_limit, ua.default_system_model_id
FROM user_agents ua WHERE ua.name LIKE '%MAX%' OR ua.name LIKE '%Max%';

SELECT gm.id AS member_id, gm.group_id, ua.id AS agent_id, ua.name, gm.agent_reply_mode,
       gm.others_may_mention, gm.require_owner_approval_on_mention, gm.muted_until,
       ua.status, ua.execution_mode, ua.daily_points_limit
FROM group_members gm
JOIN user_agents ua ON ua.id=gm.user_agent_id
WHERE gm.member_type='AGENT'
  AND gm.group_id IN (
    SELECT DISTINCT group_id FROM group_messages WHERE id BETWEEN 410 AND 435
  )
ORDER BY gm.group_id, ua.name;

SELECT adp.agent_id, ua.name, adp.day, adp.points_spent
FROM agent_daily_points adp
JOIN user_agents ua ON ua.id=adp.agent_id
WHERE adp.day=CURDATE()
  AND adp.agent_id IN (
    SELECT gm.user_agent_id FROM group_members gm
    WHERE gm.member_type='AGENT'
      AND gm.group_id IN (SELECT DISTINCT group_id FROM group_messages WHERE id BETWEEN 410 AND 435)
  );
"""

print("=== SQL ===")
print(run(f"sudo mysql aimate -e {repr(sql)}"))

print("=== LOGS ===")
print(
    run(
        "sudo journalctl -u aimate-chat --since '20 min ago' --no-pager "
        "| grep -iE 'MAX|SMART vote|rate-limited|daily limit|quota' | tail -100"
    )
)

ssh.close()
