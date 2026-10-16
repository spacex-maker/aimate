#!/usr/bin/env python3
"""Create or update an AIMATEX Account end-user for Chat SSO smoke tests."""
from __future__ import annotations

import secrets
import string
import sys
from pathlib import Path

try:
    import bcrypt
except ImportError:
    raise SystemExit("pip install bcrypt pymysql") from None

try:
    import pymysql
except ImportError:
    raise SystemExit("pip install pymysql bcrypt") from None

NAS_SECRETS = Path(r"F:\dev\proj2\aimatex-nas\secrets")
EMAIL = "chat@aimatex.com"
PASSWORD = "Aimatex@Chat2026"
DISPLAY = "Chat Demo"


def load_env(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    if not path.is_file():
        return out
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip().strip("'").strip('"')
    return out


def invite_code(n: int = 8) -> str:
    alphabet = string.ascii_uppercase + string.digits
    return "".join(secrets.choice(alphabet) for _ in range(n))


def main() -> int:
    ops: dict[str, str] = {}
    ops.update(load_env(NAS_SECRETS / "aimatex-nas.env"))
    ops.update(load_env(NAS_SECRETS / "ops.env"))
    user_env = Path(r"F:\dev\proj2\aimatex-user\secrets\aimatex-user.env")
    ops.update(load_env(user_env))

    host = ops.get("AIMATEX_USER_DB_HOST") or ops.get("MYSQL_HOST") or "175.27.255.29"
    port = int(ops.get("AIMATEX_USER_DB_PORT") or ops.get("MYSQL_PORT") or "33306")
    user = ops.get("AIMATEX_USER_DB_USER") or ops.get("MYSQL_USER") or ops.get(
        "SPRING_DATASOURCE_USERNAME"
    ) or "root"
    password = (
        ops.get("AIMATEX_USER_DB_PASSWORD")
        or ops.get("MYSQL_PASSWORD")
        or ops.get("SPRING_DATASOURCE_PASSWORD")
        or ""
    )
    if not password:
        raise SystemExit("no Account DB password in secrets")

    pw_hash = bcrypt.hashpw(PASSWORD.encode("utf-8"), bcrypt.gensalt(rounds=10)).decode("ascii")

    conn = pymysql.connect(
        host=host,
        port=port,
        user=user,
        password=password,
        database="aimatex_user",
        charset="utf8mb4",
        autocommit=True,
    )
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, email FROM users WHERE email = %s", (EMAIL,))
            row = cur.fetchone()
            if row:
                cur.execute(
                    "UPDATE users SET password_hash=%s, display_name=%s, account_enabled=1, role='USER' WHERE id=%s",
                    (pw_hash, DISPLAY, row[0]),
                )
                print(f"updated user id={row[0]} email={EMAIL}")
            else:
                code = invite_code()
                for _ in range(8):
                    cur.execute("SELECT 1 FROM users WHERE invite_code=%s", (code,))
                    if not cur.fetchone():
                        break
                    code = invite_code()
                cur.execute(
                    "INSERT INTO users (email, password_hash, display_name, role, account_enabled, invite_code, created_at) "
                    "VALUES (%s, %s, %s, 'USER', 1, %s, UTC_TIMESTAMP())",
                    (EMAIL, pw_hash, DISPLAY, code),
                )
                print(f"created user email={EMAIL} invite={code}")
    finally:
        conn.close()

    print(f"login: {EMAIL} / {PASSWORD}")
    print("portal: https://account.aimatex.com  (or via Chat login SSO)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
