#!/usr/bin/env python3
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get
import paramiko

data = load_merged()
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, username=env_get(data, "DEPLOY_SSH_USER", default="ubuntu"), password=env_get(data, "DEPLOY_SSH_PASSWORD"), timeout=30)
cmd = "sudo mysql aimate -e \"SET NAMES utf8mb4; SELECT code,name,area_sqm FROM office_plans ORDER BY sort_order;\""
stdin, stdout, stderr = ssh.exec_command(cmd, timeout=30)
sys.stdout.buffer.write(stdout.read())
ssh.close()
