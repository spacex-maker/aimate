#!/usr/bin/env python3
"""Point AIMATEX web apps at /favicon.ico (file already copied into public/)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(r"f:\dev\proj2")

ICON_BLOCK = (
    '    <link rel="icon" href="/favicon.ico" sizes="any" />\n'
    '    <link rel="shortcut icon" href="/favicon.ico" />\n'
    '    <link rel="apple-touch-icon" href="/favicon.ico" />\n'
)

FILES = [
    ROOT / "aimate/frontend/index.html",
    ROOT / "aimate/desktop/index.html",
    ROOT / "aimatex-aimodel/frontend/index.html",
    ROOT / "aimatex-user/frontend/index.html",
    ROOT / "aimatex-nas/frontend/index.html",
    ROOT / "aimatex-soul/web/index.html",
    ROOT / "aimatex-music/frontend/index.html",
    ROOT / "aimatex-miner/frontend/index.html",
    ROOT / "aimatex-robot/frontend/index.html",
    ROOT / "aimatex-home/frontend/index.html",
    ROOT / "aimatex-home/agent/frontend/index.html",
    ROOT / "aimatex-nas/client/index.html",
]

icon_href_re = re.compile(
    r"^\s*<link[^>]+rel=[\"'](?:shortcut )?icon[\"'][^>]*>\s*\n",
    re.I | re.M,
)
apple_re = re.compile(
    r"^\s*<link[^>]+rel=[\"']apple-touch-icon[\"'][^>]*>\s*\n",
    re.I | re.M,
)


def ensure_icons(text: str) -> str:
    text2 = icon_href_re.sub("", text)
    text2 = apple_re.sub("", text2)
    m = re.search(r'(<meta\s+charset=["\'][^"\']+["\']\s*/?>\s*\n)', text2, re.I)
    if m:
        return text2[: m.end()] + ICON_BLOCK + text2[m.end() :]
    m = re.search(r"(<head[^>]*>\s*\n)", text2, re.I)
    if m:
        return text2[: m.end()] + ICON_BLOCK + text2[m.end() :]
    return text2


def main() -> int:
    for p in FILES:
        if not p.exists():
            print("MISSING", p)
            continue
        raw = p.read_text(encoding="utf-8")
        if not raw.strip():
            print("EMPTY skip", p)
            continue
        if "%PUBLIC_URL%/favicon.ico" in raw:
            print("CRA already ico", p)
            continue
        new = ensure_icons(raw)
        if new != raw:
            p.write_text(new, encoding="utf-8", newline="\n")
            print("updated", p.relative_to(ROOT))
        else:
            print("unchanged", p.relative_to(ROOT))

    for ico in [
        ROOT / "aimatex/public/favicon.ico",
        ROOT / "openrobotx/public/favicon.ico",
        ROOT / "aimate/frontend/public/favicon.ico",
        ROOT / "aimatex-soul/web/public/favicon.ico",
        ROOT / "aimatex-home/frontend/public/favicon.ico",
    ]:
        print(
            ("OK" if ico.exists() else "MISSING"),
            ico.relative_to(ROOT),
            ico.stat().st_size if ico.exists() else 0,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
