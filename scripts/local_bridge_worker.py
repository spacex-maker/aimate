#!/usr/bin/env python3
"""Local Bridge worker: claim → context → complete. Token via env LOCAL_BRIDGE_TOKEN."""
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
AGENT_NAME = os.environ.get("LOCAL_BRIDGE_NAME", "Cursor · 专家")


def req(method: str, path: str, body: dict | None = None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {
        "Authorization": f"Bearer {TOKEN}",
        "Accept": "application/json",
        "Content-Type": "application/json",
    }
    r = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=90) as resp:
            raw = resp.read().decode("utf-8", "replace")
            return resp.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            j = json.loads(raw)
        except Exception:
            j = {"raw": raw[:800]}
        return e.code, j


def fmt_recent(ctx: dict) -> str:
    live = (ctx or {}).get("liveContext") or (ctx or {}).get("context") or {}
    msgs = live.get("recentMessages") or []
    lines = []
    for m in msgs[-20:]:
        who = m.get("senderName") or m.get("senderType") or "?"
        content = (m.get("content") or "").strip()
        if content:
            lines.append(f"{who}: {content}")
    return "\n".join(lines)


def build_reply(task: dict, ctx_body: dict) -> str:
    """Lightweight local reply when no external LLM is wired into this worker."""
    instruction = (task.get("instruction") or "").strip()
    ctx = ctx_body.get("context") or ctx_body.get("liveContext") or {}
    if not isinstance(ctx, dict):
        ctx = {}
    live = ctx_body.get("liveContext") or ctx
    persona = ""
    if isinstance(live, dict):
        persona = (live.get("persona") or live.get("agentPersona") or "")[:400]
    recent = fmt_recent(ctx_body)
    # Prefer answering the instruction directly; keep group-chat short.
    if not instruction:
        return "收到，但任务说明是空的，麻烦再说一遍具体要我做什么。"
    # If instruction looks like a question/command, answer briefly in character.
    tip = ""
    if persona:
        tip = f"（按人设：{persona[:80]}…）\n" if len(persona) > 80 else f"（按人设）\n"
    # Soul context optional enrichment
    soul_bits = ""
    try:
        q = instruction[:80]
        sc, soul = req("GET", f"/api/local-bridge/soul/context?q={urllib.request.quote(q)}")
        if sc == 200 and isinstance(soul, dict):
            retrieved = soul.get("retrieved") or soul.get("memories") or []
            if isinstance(retrieved, list) and retrieved:
                claims = []
                for item in retrieved[:3]:
                    if isinstance(item, dict):
                        c = item.get("claim") or item.get("content") or item.get("text")
                        if c:
                            claims.append(str(c)[:120])
                if claims:
                    soul_bits = "参考记忆：" + "；".join(claims) + "\n"
    except Exception:
        pass

    # Practical reply for coding agent: acknowledge + next step from instruction
    reply = (
        f"收到：{instruction[:500]}\n"
        f"{soul_bits}"
        f"我这边（{AGENT_NAME}）已在本机 worker 接单。"
        f"若需要我改仓库/跑命令，请把路径或目标再具体一点；"
        f"当前会话可继续 @ 我派活。"
    )
    if recent:
        # Don't dump chat; just signal we saw context
        reply += f"（已读近 {min(20, recent.count(chr(10)) + 1 if recent else 0)} 条群上下文）"
    return reply.strip()


def handle_task(task: dict) -> None:
    tid = task.get("id") or task.get("publicId")
    if not tid:
        print("[worker] task missing id", task, flush=True)
        return
    print(f"[worker] claimed {tid} instruction={(task.get('instruction') or '')[:120]!r}", flush=True)
    code, ctx = req("GET", f"/api/local-bridge/tasks/{tid}/context")
    if code != 200:
        req("POST", f"/api/local-bridge/tasks/{tid}/complete", {"failed": True, "error": f"context HTTP {code}: {ctx}"})
        print(f"[worker] context fail {code} {ctx}", flush=True)
        return
    try:
        result = build_reply(task, ctx if isinstance(ctx, dict) else {})
        sc, out = req("POST", f"/api/local-bridge/tasks/{tid}/complete", {"result": result})
        print(f"[worker] complete {tid} -> {sc} {out}", flush=True)
    except Exception as e:
        req("POST", f"/api/local-bridge/tasks/{tid}/complete", {"failed": True, "error": str(e)[:500]})
        print(f"[worker] error {e}", flush=True)


def main() -> int:
    if not TOKEN:
        print("LOCAL_BRIDGE_TOKEN required", file=sys.stderr)
        return 1
    print(f"[worker] base={BASE} interval={INTERVAL}s name={AGENT_NAME}", flush=True)
    code, me = req("GET", "/api/local-bridge/me")
    print(f"[worker] me {code} {me}", flush=True)
    if code != 200:
        return 1
    while True:
        try:
            code, body = req("POST", "/api/local-bridge/tasks/claim", {})
            if code != 200:
                print(f"[worker] claim {code} {body}", flush=True)
                time.sleep(INTERVAL)
                continue
            task = (body or {}).get("task") if isinstance(body, dict) else None
            if task:
                handle_task(task)
            else:
                print("[worker] idle", flush=True)
        except Exception as e:
            print(f"[worker] loop error: {e}", flush=True)
        time.sleep(INTERVAL)


if __name__ == "__main__":
    raise SystemExit(main())
