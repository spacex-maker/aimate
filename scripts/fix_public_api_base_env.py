#!/usr/bin/env python3
from __future__ import annotations

import sys
import time
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, REMOTE_JAVA, load_merged, env_get  # noqa: E402


def main() -> int:
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
    remote = f"{REMOTE_JAVA}/aimate.env"
    key = "AIMATE_PUBLIC_API_BASE"
    val = "https://chat-api.aimatex.com"
    script = f"""
from pathlib import Path
p = Path({remote!r})
lines = p.read_text().splitlines()
out, found = [], False
for ln in lines:
    if ln.startswith({key!r} + "="):
        out.append({key!r} + "=" + {val!r})
        found = True
    else:
        out.append(ln)
if not found:
    out.append({key!r} + "=" + {val!r})
p.write_text("\\n".join(out) + "\\n")
print("ok", {key!r}, {val!r})
"""
    _, o, e = ssh.exec_command("python3 -", timeout=30)
    o.channel.send(script.encode())
    o.channel.shutdown_write()
    print(o.read().decode())
    err = e.read().decode()
    if err:
        print(err, file=sys.stderr)
    _, o2, _ = ssh.exec_command("sudo systemctl restart aimate-chat", timeout=60)
    o2.channel.recv_exit_status()
    time.sleep(14)
    _, o3, _ = ssh.exec_command(
        "sudo systemctl is-active aimate-chat; "
        f"grep ^{key}= {remote}",
        timeout=30,
    )
    print(o3.read().decode())
    ssh.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
