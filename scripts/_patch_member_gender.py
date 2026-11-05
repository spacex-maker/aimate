from pathlib import Path
p = Path(r"f:\dev\proj2\aimate\src\main\java\com\openforge\aimate\group\GroupChatService.java")
raw = p.read_bytes()
t = raw.decode("utf-16") if len(raw) > 2 and raw[1] == 0 else raw.decode("utf-8")
if "genderLabelForMember" not in t:
    helper = """
    private static String genderLabelForMember(String gender) {
        if (!StringUtils.hasText(gender)) return null;
        return switch (gender.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "FEMALE", "女", "F" -> "女";
            case "MALE", "男", "M" -> "男";
            case "OTHER", "其他" -> "其他";
            default -> null;
        };
    }

"""
    anchor = "    private static String trim(String s, int max) {"
    if anchor not in t:
        raise SystemExit("trim anchor missing")
    t = t.replace(anchor, helper + anchor, 1)
    print("helper inserted")
else:
    print("helper exists")
if "genderLabelForMember(ua.getGender())" not in t:
    raise SystemExit("call site missing - gender patch may have failed")
p.write_text(t, encoding="utf-8")
print("GroupChatService ok")