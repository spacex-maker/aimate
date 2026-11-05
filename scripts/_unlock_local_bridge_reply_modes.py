# -*- coding: utf-8 -*-
from pathlib import Path
import re
p = Path(r"f:/dev/proj2/aimate/src/main/java/com/openforge/aimate/group/GroupChatService.java")
t = p.read_text(encoding="utf-8")
pat = re.compile(
    r\"\n\\s*if \(ua\.getExecutionMode\(\) == AgentExecutionMode\.LOCAL_BRIDGE\\s*"
    r\"&& mode != GroupMember\.AgentReplyMode\.MENTION_ONLY\\s*"
    r\"&& mode != GroupMember\.AgentReplyMode\.SILENT\) \{\\s*"
    r\throw new ResponseStatusException\(\\s*"
    r\HttpStatus\.BAL_REQUEST,\\s*"
    r\"[^\"]+\"\\s*"
    r\\);\\s*"
    r\\}\\s*",
    re.M,
)
nt,"n = pat.subn('\n', t)
print('removed', n)
if n != 2:
    raise SystemExit(1)
p.write_text(nt, encoding='utf-8')
print('ok')
