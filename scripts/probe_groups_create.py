#!/usr/bin/env python3
"""Login and try create group on prod to reproduce 401."""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import load_merged, env_get  # noqa: E402

API = "https://chat-api.aimatex.com"


def req(method: str, path: str, body=None, token=None):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            raw = resp.read().decode()
            print(f"{method} {path} -> {resp.status}")
            print(raw[:800])
            return resp.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        print(f"{method} {path} -> {e.code}")
        print(raw[:800])
        return e.code, None


def main() -> int:
    data = load_merged()
    # try bootstrap admin as user? better use a known chat user if present
    user = env_get(data, "AIMATE_PROBE_USER", "DEPLOY_PROBE_USER")
    password = env_get(data, "AIMATE_PROBE_PASSWORD", "DEPLOY_PROBE_PASSWORD")
    email = env_get(data, "AIMATE_ADMIN_BOOTSTRAP_EMAIL")
    admin_pw = env_get(data, "AIMATE_ADMIN_BOOTSTRAP_PASSWORD")

    token = None
    if user and password:
        code, body = req("POST", "/api/auth/login", {"identifier": user, "password": password})
        if code == 200 and body:
            token = body.get("token")
    if not token and email and admin_pw:
        # user login with bootstrap email may fail (admin is separate)
        code, body = req("POST", "/api/auth/login", {"identifier": email, "password": admin_pw})
        if code == 200 and body:
            token = body.get("token")

    if not token:
        print("No probe credentials; checking unauthenticated + me/agents path only")
        req("GET", "/api/groups")
        req("POST", "/api/groups", {"title": "t"})
        return 0

    print("token ok, len=", len(token))
    req("GET", "/api/me/agents", token=token)
    req("GET", "/api/groups", token=token)
    req("POST", "/api/groups", {"title": "probe-group", "includeSecretary": False}, token=token)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
