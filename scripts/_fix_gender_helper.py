# -*- coding: utf-8 -*-
from pathlib import Path

p = Path(r"f:\dev\proj2\aimate\src\main\java\com\openforge\aimate\group\GroupChatService.java")
t = p.read_text(encoding="utf-8")
t = t.replace("\r\n", "\n").replace("\r", "\n")

helper = (
    "    private static String genderLabelForMember(String gender) {\n"
    "        if (!StringUtils.hasText(gender)) return null;\n"
    "        return switch (gender.trim().toUpperCase(java.util.Locale.ROOT)) {\n"
    '            case "FEMALE", "\u5973", "F" -> "\u5973";\n'
    '            case "MALE", "\u7537", "M" -> "\u7537";\n'
    '            case "OTHER", "\u5176\u4ed6" -> "\u5176\u4ed6";\n'
    "            default -> null;\n"
    "        };\n"
    "    }\n\n"
)

if "private static String genderLabelForMember" not in t:
    anchor = "    private static String trim(String s, int max) {"
    if anchor not in t:
        raise SystemExit("trim anchor missing")
    t = t.replace(anchor, helper + anchor, 1)
    print("inserted")
else:
    print("exists")

if "genderLabelForMember(ua.getGender())" not in t:
    raise SystemExit("call site missing")

p.write_text(t, encoding="utf-8", newline="\n")
print("ok lines", t.count("\n"))
