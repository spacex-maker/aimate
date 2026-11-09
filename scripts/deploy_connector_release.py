#!/usr/bin/env python3
"""Upload Aimate Connector Windows installer to chat.aimatex.com/downloads/."""
from __future__ import annotations

import os
import sys
from pathlib import Path

import paramiko

HOST = "139.155.130.167"
REMOTE_DOWNLOADS = "/home/ubuntu/web/chat/downloads"
ROOT = Path(__file__).resolve().parents[1]
CONNECTOR_ROOT = ROOT.parent / "aimate-connector"
RELEASE = CONNECTOR_ROOT / "release"
VERSIONED_REMOTE = "Aimate-Connector-0.1.0-setup.exe"
STABLE_NAME = "Aimate-Connector-setup.exe"

NAS_SECRETS = Path(r"F:\dev\proj2\aimatex-nas\secrets")
DEPLOY_ENV_CANDIDATES = (
    Path.home() / ".aimatex-chat-deploy.env",
    Path.home() / ".aimatex-music-deploy.env",
    ROOT / "secrets" / "deploy.env",
)


def load_env_file(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    out: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip().strip("'").strip('"')
    return out


def load_merged() -> dict[str, str]:
    data: dict[str, str] = {}
    data.update(load_env_file(NAS_SECRETS / "aimatex-nas.env"))
    data.update(load_env_file(NAS_SECRETS / "ops.env"))
    for path in DEPLOY_ENV_CANDIDATES:
        data.update(load_env_file(path))
    data.update(load_env_file(ROOT / "secrets" / "aimate.env"))
    for k, v in os.environ.items():
        if v:
            data[k] = v
    return data


def env_get(data: dict[str, str], *keys: str, default: str = "") -> str:
    for k in keys:
        v = (data.get(k) or "").strip()
        if v:
            return v
    return default


def find_installer() -> Path:
    preferred = RELEASE / "Aimate Connector-0.1.0-setup.exe"
    if preferred.exists():
        return preferred
    alts = [
        a
        for a in list(RELEASE.glob("*Connector*setup*.exe")) + list(RELEASE.glob("*.exe"))
        if a.is_file() and "unpacked" not in str(a)
    ]
    if not alts:
        raise SystemExit(f"missing installer under {RELEASE}")
    return sorted(alts, key=lambda p: p.stat().st_mtime, reverse=True)[0]


def ensure_nginx_downloads(ssh: paramiko.SSHClient) -> None:
    helper = (
        "from pathlib import Path\n"
        "import re\n"
        "cands = [\n"
        "    Path('/etc/nginx/sites-available/chat.aimatex.com'),\n"
        "    Path('/etc/nginx/sites-enabled/chat.aimatex.com'),\n"
        "]\n"
        "for p in cands:\n"
        "    if not p.exists():\n"
        "        continue\n"
        "    t = p.read_text(encoding='utf-8', errors='replace')\n"
        "    if 'location ^~ /downloads/' in t:\n"
        "        print(f'ok-exists {p}')\n"
        "        break\n"
        "    needle = '    location / {\\n        try_files $uri $uri/ /index.html;\\n    }'\n"
        "    insert = (\n"
        "        '    location ^~ /downloads/ {\\n'\n"
        "        '        default_type application/octet-stream;\\n'\n"
        "        '        add_header Content-Disposition \"attachment\";\\n'\n"
        "        '        try_files $uri =404;\\n'\n"
        "        '    }\\n\\n'\n"
        "        + needle\n"
        "    )\n"
        "    if needle in t:\n"
        "        p.write_text(t.replace(needle, insert, 1), encoding='utf-8')\n"
        "        print(f'patched {p}')\n"
        "        break\n"
        "    m = re.search(r'location /\\s*\\{[^}]*try_files \\$uri \\$uri/ /index\\.html;[^}]*\\}', t)\n"
        "    if m:\n"
        "        old = m.group(0)\n"
        "        new = (\n"
        "            'location ^~ /downloads/ {\\n'\n"
        "            '        default_type application/octet-stream;\\n'\n"
        "            '        add_header Content-Disposition \"attachment\";\\n'\n"
        "            '        try_files $uri =404;\\n'\n"
        "            '    }\\n\\n    ' + old\n"
        "        )\n"
        "        p.write_text(t.replace(old, new, 1), encoding='utf-8')\n"
        "        print(f'regex-patched {p}')\n"
        "        break\n"
        "    print(f'skip {p}')\n"
    )
    sftp = ssh.open_sftp()
    with sftp.file("/tmp/patch_chat_downloads_nginx.py", "w") as f:
        f.write(helper)
    sftp.close()
    _, stdout, stderr = ssh.exec_command(
        "sudo python3 /tmp/patch_chat_downloads_nginx.py; sudo nginx -t && sudo systemctl reload nginx",
        timeout=60,
    )
    print(stdout.read().decode(errors="replace"))
    err = stderr.read().decode(errors="replace")
    if err.strip():
        print(err[:800])


def main() -> int:
    src = find_installer()
    print("local:", src, src.stat().st_size)
    data = load_merged()
    user = env_get(data, "DEPLOY_SSH_USER", default="ubuntu")
    password = env_get(data, "DEPLOY_SSH_PASSWORD")
    if not password:
        print("missing DEPLOY_SSH_PASSWORD", file=sys.stderr)
        return 1
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(
        HOST,
        username=user,
        password=password,
        timeout=30,
        allow_agent=False,
        look_for_keys=False,
    )
    ssh.exec_command(f"mkdir -p {REMOTE_DOWNLOADS}", timeout=30)
    sftp = ssh.open_sftp()
    remote_ver = f"{REMOTE_DOWNLOADS}/{VERSIONED_REMOTE}"
    remote_stable = f"{REMOTE_DOWNLOADS}/{STABLE_NAME}"
    print("upload", remote_ver)
    sftp.put(str(src), remote_ver)
    print("upload", remote_stable)
    sftp.put(str(src), remote_stable)
    sftp.close()
    ensure_nginx_downloads(ssh)
    _, out, _ = ssh.exec_command(f"ls -la {REMOTE_DOWNLOADS}", timeout=30)
    print(out.read().decode(errors="replace"))
    ssh.close()
    print("done: https://chat.aimatex.com/downloads/Aimate-Connector-setup.exe")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
