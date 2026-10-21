#!/usr/bin/env python3
"""Register aimatex-chat OAuth client + platform on AIMATEX Account."""
from __future__ import annotations

import base64
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NAS_SECRETS = Path(r"F:\dev\proj2\aimatex-nas\secrets")
CHAT_SECRETS = ROOT / "secrets" / "aimate.env"
API = "https://account-api.aimatex.com"
CLIENT_ID = "aimatex-chat"


def load_env(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    if not path.is_file():
        return out
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip()
    return out


def http_json(method, path, body=None, token=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(API + path, data=data, method=method)
    req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode()
            return r.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode(errors="replace")
        print("HTTP", e.code, path, raw[:1200])
        raise


def admin_login(ops: dict[str, str]) -> str:
    email = (
        ops.get("AIMATEX_USER_ADMIN_BOOTSTRAP_EMAIL")
        or ops.get("NAS_ADMIN_BOOTSTRAP_EMAIL")
        or "admin@aimatex.com"
    )
    password = ops.get("AIMATEX_USER_ADMIN_BOOTSTRAP_PASSWORD") or ops.get(
        "NAS_ADMIN_BOOTSTRAP_PASSWORD"
    )
    if not password:
        raise SystemExit("no admin password in nas secrets/ops.env")
    _, cap = http_json("GET", "/api/auth/captcha")
    img = cap["imageBase64"]
    b64 = img.split(",", 1)[1] if img.startswith("data:") else img
    svg = base64.b64decode(b64).decode("utf-8", errors="replace")
    m = re.search(r"<text[^>]*>([^<]+)</text>", svg)
    if not m:
        raise SystemExit("cannot parse captcha")
    _, login = http_json(
        "POST",
        "/api/admin/auth/login",
        {
            "email": email,
            "password": password,
            "captchaId": cap["captchaId"],
            "captchaCode": m.group(1).strip(),
            "portal": "CONSOLE",
        },
    )
    token = login.get("accessToken") or login.get("token")
    if not token:
        raise SystemExit("no accessToken")
    return token


def upsert_env(path: Path, updates: dict[str, str]) -> None:
    existing = load_env(path) if path.exists() else {}
    existing.update({k: v for k, v in updates.items() if v is not None})
    lines = [f"{k}={v}" for k, v in existing.items()]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def patch_admin_login(token: str) -> None:
    """只补后台登录地址，保留图标等已有字段。"""
    admin_url = "https://chat-admin.aimatex.com"
    # 不用 q=：Account 侧 description 等字段为 CLOB 时 lower(q) 会 500
    _, page = http_json("GET", "/api/admin/platforms?size=100", token=token)
    items = page.get("items") or []
    target = next(
        (p for p in items if p.get("clientId") == CLIENT_ID or p.get("code") == "chat"),
        None,
    )
    if not target:
        raise SystemExit("chat platform not found")
    body = {
        "code": target.get("code"),
        "name": target.get("name"),
        "description": target.get("description"),
        "homeUrl": target.get("homeUrl"),
        "iconUrl": target.get("iconUrl"),
        "backgroundUrl": target.get("backgroundUrl"),
        "adminLoginUrl": admin_url,
        "healthUrl": target.get("healthUrl"),
        "clientId": target.get("clientId"),
        "companyId": target.get("companyId"),
        "enabled": target.get("enabled"),
        "sortOrder": target.get("sortOrder"),
    }
    _, updated = http_json("PUT", f"/api/admin/platforms/{target['id']}", body, token=token)
    print(
        "patched",
        updated.get("code"),
        updated.get("clientId"),
        "adminLoginUrl=",
        updated.get("adminLoginUrl"),
    )


def main() -> int:
    ops = {}
    ops.update(load_env(NAS_SECRETS / "aimatex-nas.env"))
    ops.update(load_env(NAS_SECRETS / "ops.env"))
    token = admin_login(ops)
    print("admin login ok")
    if "--patch-admin-url" in sys.argv:
        patch_admin_login(token)
        return 0

    _, resp = http_json(
        "POST",
        "/api/admin/integration/apps",
        {
            "clientId": CLIENT_ID,
            "clientName": "AIMATEX Chat",
            "redirectUris": (
                "https://chat.aimatex.com/oauth/callback,"
                "http://127.0.0.1:5173/oauth/callback,"
                "http://localhost:5173/oauth/callback"
            ),
            "homeUrl": "https://chat.aimatex.com",
            "adminLoginUrl": "https://chat-admin.aimatex.com",
            "healthUrl": "https://chat-api.aimatex.com/actuator/health",
            "description": "OpenForgeX / AIMATEX Chat — Autonomous Agent",
            "platformCode": "chat",
            "platformName": "AIMATEX Chat",
            "createPlatform": True,
            "requirePkce": True,
            "companyId": 1,
            "enabled": True,
            "sortOrder": 3,
        },
        token=token,
    )
    print(
        "app registered: created=",
        resp.get("created"),
        "platform=",
        (resp.get("platform") or {}).get("code"),
    )

    updates = {
        "AIMATEX_OIDC_ENABLED": "true",
        "AIMATEX_OIDC_CLIENT_ID": CLIENT_ID,
        "AIMATEX_OIDC_ISSUER": "https://account-api.aimatex.com",
        "AIMATEX_OIDC_AUTHORIZE_URL": "https://account.aimatex.com/oauth/authorize",
        "AIMATEX_OIDC_TOKEN_URL": "https://account-api.aimatex.com/oauth/token",
        "AIMATEX_OIDC_USERINFO_URL": "https://account-api.aimatex.com/oauth/userinfo",
        "AIMATEX_OIDC_JWKS_URL": "https://account-api.aimatex.com/oauth/jwks",
        "AIMATEX_OIDC_REDIRECT_URI": "https://chat.aimatex.com/oauth/callback",
        "AIMATEX_OIDC_REDIRECT_URIS": (
            "https://chat.aimatex.com/oauth/callback,"
            "http://127.0.0.1:5173/oauth/callback,"
            "http://localhost:5173/oauth/callback"
        ),
        "AIMATEX_CLIENT_ID": CLIENT_ID,
    }
    upsert_env(CHAT_SECRETS, updates)
    print("secrets updated:", CHAT_SECRETS)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
