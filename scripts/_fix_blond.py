# -*- coding: utf-8 -*-
from pathlib import Path
import re

p = Path(r"f:\dev\proj2\aimate\frontend\src\lib\emojiCatalog.ts")
t = p.read_text(encoding="utf-8")
repl = "'" + "\U0001F471\u200D\u2642\uFE0F" + "'"
t2, n = re.subn(r"'[^']*blond[^']*'", repl, t)
print("replaced", n)
p.write_text(t2, encoding="utf-8")
