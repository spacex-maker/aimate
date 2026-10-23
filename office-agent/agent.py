#!/usr/bin/env python3
"""AIMATEX Office Agent — runs on rented CVM (private network only)."""
import json
import os
import subprocess
from http.server import BaseHTTPRequestHandler, HTTPServer

TOKEN = os.environ.get("OFFICE_TOKEN", "")
PORT = int(os.environ.get("OFFICE_AGENT_PORT", "18765"))


class H(BaseHTTPRequestHandler):
    def _auth(self):
        return self.headers.get("X-Office-Token", "") == TOKEN

    def _json(self, code, obj):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read(self):
        n = int(self.headers.get("Content-Length", "0"))
        return json.loads(self.rfile.read(n).decode() or "{}")

    def do_GET(self):
        if self.path.startswith("/health"):
            self._json(200, {"ok": True})
            return
        self._json(404, {"error": "not found"})

    def do_POST(self):
        if not self._auth():
            self._json(401, {"error": "unauthorized"})
            return
        data = self._read()
        if self.path.startswith("/exec"):
            cmd = data.get("command") or ""
            timeout = int(data.get("timeout_seconds") or 120)
            try:
                p = subprocess.run(
                    ["bash", "-lc", cmd],
                    capture_output=True,
                    text=True,
                    timeout=timeout,
                )
                self._json(
                    200,
                    {
                        "exitCode": p.returncode,
                        "stdout": (p.stdout or "")[-50000:],
                        "stderr": (p.stderr or "")[-20000:],
                    },
                )
            except subprocess.TimeoutExpired:
                self._json(200, {"exitCode": 124, "stdout": "", "stderr": "timeout"})
            return
        if self.path.startswith("/write"):
            path = data.get("path") or ""
            content = data.get("content") or ""
            os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
            with open(path, "w", encoding="utf-8") as f:
                f.write(content)
            self._json(200, {"ok": True, "path": path})
            return
        if self.path.startswith("/install"):
            pkgs = data.get("packages") or []
            if isinstance(pkgs, str):
                pkgs = pkgs.split()
            cmd = (
                "apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y "
                + " ".join(pkgs)
            )
            p = subprocess.run(["bash", "-lc", cmd], capture_output=True, text=True, timeout=600)
            self._json(
                200,
                {
                    "exitCode": p.returncode,
                    "stdout": (p.stdout or "")[-50000:],
                    "stderr": (p.stderr or "")[-20000:],
                },
            )
            return
        self._json(404, {"error": "not found"})

    def log_message(self, fmt, *args):
        return


if __name__ == "__main__":
    HTTPServer(("0.0.0.0", PORT), H).serve_forever()
