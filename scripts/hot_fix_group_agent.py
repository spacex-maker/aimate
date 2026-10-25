#!/usr/bin/env python3
"""Package jar, upload, patch PRIMARY_LLM keys from system_config DEEPSEEK, restart chat."""
from __future__ import annotations

import subprocess
import sys
import time
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import (  # noqa: E402
    HOST,
    REMOTE_JAR,
    JAR,
    load_merged,
    env_get,
)

MVN = Path(r"F:\dev\tools\apache-maven-3.9.6\bin\mvn.cmd")


def run_ssh(ssh: paramiko.SSHClient, cmd: str, timeout: int = 180) -> tuple[int, str]:
    print(">>>", cmd[:200])
    _, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    if out.strip():
        print(out[-3000:])
    if err.strip():
        print("ERR:", err[-1500:])
    return code, out + err


def main() -> int:
    print("Building jar ...")
    env = {
        **dict(**{k: __import__("os").environ[k] for k in __import__("os").environ}),
        "JAVA_HOME": r"F:\dev\jdk\jdk-17.0.18.8-hotspot",
    }
    # Prefer project jdk25 if compile needs 25 — pom says release 25
    jdk25 = ROOT / "jdk25"
    if jdk25.exists():
        env["JAVA_HOME"] = str(jdk25)
    subprocess.check_call(
        [str(MVN), "-DskipTests", "package", "-q"],
        cwd=str(ROOT),
        env={**env, "Path": env["JAVA_HOME"] + r"\bin;" + env.get("Path", "")},
    )
    if not JAR.exists():
        print("JAR missing", JAR, file=sys.stderr)
        return 1

    d = load_merged()
    user = env_get(d, "DEPLOY_SSH_USER", default="ubuntu")
    password = env_get(d, "DEPLOY_SSH_PASSWORD")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=user, password=password, timeout=30, allow_agent=False, look_for_keys=False)

    print("Uploading", JAR, "->", REMOTE_JAR)
    sftp = ssh.open_sftp()
    sftp.put(str(JAR), REMOTE_JAR)
    sftp.close()

    # Sync PRIMARY/FALLBACK keys from system_config DEEPSEEK_API_KEY so LlmRouter also works
    patch = r"""
python3 <<'PY'
from pathlib import Path
import subprocess, re
env_path = Path('/home/ubuntu/java/chat/aimate.env')
text = env_path.read_text(encoding='utf-8')
# load db
edata={}
for line in text.splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,v=line.split('=',1); edata[k.strip()]=v.strip()
sql="SELECT config_value FROM system_config WHERE config_key='DEEPSEEK_API_KEY' LIMIT 1;"
p=subprocess.run(['mysql','-h'+edata.get('DB_HOST','127.0.0.1'),'-P'+edata.get('DB_PORT','3306'),
 '-u'+edata.get('DB_USERNAME','aimate'),'-p'+edata.get('DB_PASSWORD',''),edata.get('DB_NAME','aimate'),'-N'],
 input=sql, capture_output=True, text=True)
key=(p.stdout or '').strip().splitlines()
key=key[0].strip() if key else ''
if not key or 'placeholder' in key.lower():
    print('NO_KEY')
    raise SystemExit(0)
def set_kv(src, k, v):
    pat=re.compile(r'^'+re.escape(k)+r'=.*$', re.M)
    line=f'{k}={v}'
    if pat.search(src):
        return pat.sub(line, src, count=1)
    return src.rstrip()+'\n'+line+'\n'
text2=set_kv(text, 'PRIMARY_LLM_API_KEY', key)
text2=set_kv(text2, 'FALLBACK_LLM_API_KEY', key)
text2=set_kv(text2, 'PRIMARY_LLM_BASE_URL', 'https://api.deepseek.com/v1')
text2=set_kv(text2, 'FALLBACK_LLM_BASE_URL', 'https://api.deepseek.com/v1')
text2=set_kv(text2, 'PRIMARY_LLM_MODEL', 'deepseek-flash')
text2=set_kv(text2, 'FALLBACK_LLM_MODEL', 'deepseek-chat')
env_path.write_text(text2, encoding='utf-8')
print('PATCHED_KEYS ok prefix='+key[:6]+' len='+str(len(key)))
PY
"""
    run_ssh(ssh, patch, timeout=60)
    run_ssh(ssh, "sudo systemctl restart aimate-chat", timeout=60)
    time.sleep(8)
    run_ssh(ssh, "sudo systemctl is-active aimate-chat; curl -sS http://127.0.0.1:9299/api/health; sudo journalctl -u aimate-chat -n 30 --no-pager", timeout=60)
    ssh.close()
    print("DONE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
