#!/usr/bin/env python3
"""Poll claim until a task appears; print JSON and exit 0. Env: LOCAL_BRIDGE_TOKEN, LOCAL_BRIDGE_BASE."""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE = os.environ.get("LOCAL_BRIDGE_BASE", "https://chat-api.aimatex.com").rstrip("/")
TOKEN = os.environ.get("LOCAL_BRIDGE_TOKEN", "").strip()
INTERVAL = float(os.environ.get("LOCAL_BRIDGE_INTERVAL", "8"))
MAX_WAIT = float(os.environ.get("LOCAL_BRIDGE_MAX_WAIT", "600"))


def req(method: str, path: str, body: dict | None = None):
    data = None if body is None else json.dumps(body).encode()
    r = urllib.request.Request(
        BASE + path,
        data=data,
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "Accept": "application/json",
            "Content-Type": "application/json",
        },
        method=method,
    )
    with urllib.request.urlopen(r, timeout=60) as resp:
        raw = resp.read().decode()
        return json.loads(raw) if raw else {}


def main() -> int:
    if not TOKEN:
        print("LOCAL_BRIDGE_TOKEN required", file=sys.stderr)
        return 1
    deadline = time.time() + MAX_WAIT
    while time.time() < deadline:
        try:
            # 长轮询：服务端最多挂 25s，有任务立刻返回（仍兼容短轮询）
            body = req("POST", "/api/local-bridge/tasks/claim?waitSeconds=25", {})
            task = body.get("task")
            if task:
                tid = task.get("id") or task.get("publicId")
                ctx = req("GET", f"/api/local-bridge/tasks/{tid}/context")
                out = {"task": task, "context": ctx}
                print("TASK_READY")
                print(json.dumps(out, ensure_ascii=False))
                return 0
            print("idle", flush=True)
        except urllib.error.HTTPError as e:
            print(f"http {e.code}: {e.read()[:200]}", flush=True)
        except Exception as e:
            print(f"err {e}", flush=True)
        time.sleep(INTERVAL)
    print("timeout", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
