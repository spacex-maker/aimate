#!/usr/bin/env python3
"""mvn package + upload jar + restart aimate-chat (no env rewrite)."""
from __future__ import annotations

import os
import subprocess
import sys
import time
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, JAR, REMOTE_JAR, load_merged, env_get  # noqa: E402

MVN = Path(r"F:\dev\tools\apache-maven-3.9.6\bin\mvn.cmd")


def main() -> int:
    jdk = ROOT / "jdk25"
    java_home = str(jdk if jdk.exists() else Path(r"F:\dev\jdk\jdk-17.0.18.8-hotspot"))
    env = {**os.environ, "JAVA_HOME": java_home, "Path": java_home + r"\bin;" + os.environ.get("Path", "")}
    print("Building ...")
    subprocess.check_call([str(MVN), "-DskipTests", "package", "-q"], cwd=str(ROOT), env=env)
    if not JAR.exists():
        print("missing jar", file=sys.stderr)
        return 1

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
    print("Upload", JAR.name)
    sftp = ssh.open_sftp()
    sftp.put(str(JAR), REMOTE_JAR)
    sftp.close()
    _, o, e = ssh.exec_command("sudo systemctl restart aimate-chat", timeout=60)
    o.channel.recv_exit_status()
    time.sleep(14)
    _, o2, _ = ssh.exec_command(
        "sudo systemctl is-active aimate-chat; curl -sS http://127.0.0.1:9299/api/health",
        timeout=30,
    )
    print(o2.read().decode())
    ssh.close()
    print("DONE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
