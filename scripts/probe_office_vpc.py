#!/usr/bin/env python3
"""Probe chat CVM VPC metadata on prod and print AIMATE_OFFICE_* hints."""
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get  # noqa: E402

PROBE = r"""
set -e
echo '=== private IP ==='
hostname -I | awk '{print $1}'
echo '=== metadata (tencent) ==='
curl -s --connect-timeout 2 http://metadata.tencentyun.com/latest/meta-data/local-ipv4 || true
echo
curl -s --connect-timeout 2 http://metadata.tencentyun.com/latest/meta-data/placement/region || true
echo
curl -s --connect-timeout 2 http://metadata.tencentyun.com/latest/meta-data/vpc-id || true
echo
curl -s --connect-timeout 2 http://metadata.tencentyun.com/latest/meta-data/subnet-id || true
echo
echo '=== env office keys (no secrets) ==='
grep -E '^AIMATE_OFFICE_' /home/ubuntu/java/chat/aimate.env 2>/dev/null || true
"""


def main() -> int:
    data = load_merged()
    password = env_get(data, "DEPLOY_SSH_PASSWORD")
    user = env_get(data, "DEPLOY_SSH_USER", default="ubuntu")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=user, password=password, timeout=30)
    sftp = ssh.open_sftp()
    remote = "/tmp/probe_office_vpc.sh"
    with sftp.file(remote, "w") as f:
        f.write(PROBE)
    sftp.close()
    stdin, stdout, stderr = ssh.exec_command(f"bash {remote}", timeout=60)
    sys.stdout.buffer.write(stdout.read())
    sys.stderr.buffer.write(stderr.read())
    code = stdout.channel.recv_exit_status()
    ssh.exec_command(f"rm -f {remote}")
    ssh.close()
    return code


if __name__ == "__main__":
    raise SystemExit(main())
