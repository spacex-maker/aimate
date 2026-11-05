# -*- coding: utf-8 -*-
from pathlib import Path

p = Path(r"f:\dev\proj2\aimate\frontend\src\components\agent\AgentInputBox.tsx")
t = p.read_text(encoding="utf-8")
old = "import { Send, ImagePlus, SlidersHorizontal, Mic, ChevronDown, X } from 'lucide-react'"
new = "import { Send, ImagePlus, SlidersHorizontal, Mic, ChevronDown, X, Smile } from 'lucide-react'"
if old in t:
    t = t.replace(old, new, 1)
    print("fixed")
elif ", Smile }" in t or "Smile } from" in t:
    print("already")
else:
    raise SystemExit("import line not found")
p.write_text(t, encoding="utf-8", newline="\n")
