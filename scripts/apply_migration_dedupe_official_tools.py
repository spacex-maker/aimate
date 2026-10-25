#!/usr/bin/env python3
"""Apply migration_dedupe_official_agent_tools.sql + refresh seed_builtin_tools on chat prod DB."""
from __future__ import annotations

import sys
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
    sftp = ssh.open_sftp()
    remote_dir = f"{REMOTE_JAVA}/db"
    for name in (
        "migration_dedupe_official_agent_tools.sql",
        "seed_builtin_tools.sql",
    ):
        local = ROOT / "src" / "main" / "resources" / "db" / name
        remote = f"{remote_dir}/{name}"
        sftp.put(str(local), remote)
        print("put", name)
    sftp.close()
    for name in (
        "migration_dedupe_official_agent_tools.sql",
        "seed_builtin_tools.sql",
    ):
        cmd = f"sudo mysql aimate < {remote_dir}/{name}"
        print(">>>", cmd)
        _stdin, stdout, stderr = ssh.exec_command(cmd, timeout=120)
        out = (stdout.read() + stderr.read()).decode("utf-8", errors="replace")
        code = stdout.channel.recv_exit_status()
        if out.strip():
            print(out)
        if code != 0:
            print("FAILED", name, "exit", code, file=sys.stderr)
            ssh.close()
            return code
    # sanity count
    _stdin, stdout, stderr = ssh.exec_command(
        "sudo mysql aimate -N -e \"SELECT tool_name, COUNT(*) c FROM agent_tools WHERE user_id IS NULL AND is_active=1 GROUP BY tool_name ORDER BY tool_name;\"",
        timeout=60,
    )
    print((stdout.read() + stderr.read()).decode("utf-8", errors="replace"))
    ssh.close()
    print("DONE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
