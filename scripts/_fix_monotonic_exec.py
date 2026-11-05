# -*- coding: utf-8 -*-
from pathlib import Path

p = Path(r"f:\dev\proj2\aimate\src\main\java\com\openforge\aimate\localbridge\LocalBridgeService.java")
t = p.read_text(encoding="utf-8")

old = """        if (\"thinking\".equals(kind)) {
            task.setThinkingText(body);
        } else {
            task.setExecutionText(body);
        }
        taskRepository.save(task);"""

new = """        if (\"thinking\".equals(kind)) {
            task.setThinkingText(body);
        } else {
            // 本机执行过程须单调增长；禁止用无关长文（常见：定稿正文）覆盖已有 .partial 增量
            String prevEx = task.getExecutionText();
            if (shouldKeepPriorExecution(prevEx, body)) {
                Map<String, Object> keep = new LinkedHashMap<>();
                keep.put(\"ok\", true);
                keep.put(\"superseded\", false);
                keep.put(\"streaming\", true);
                keep.put(\"unchanged\", true);
                keep.put(\"keptPriorExecution\", true);
                keep.put(\"kind\", kind);
                keep.put(\"status\", task.getStatus().name());
                return keep;
            }
            task.setExecutionText(body);
        }
        taskRepository.save(task);"""

old = old.replace('\\"', '"')
new = new.replace('\\"', '"')
if old not in t:
    raise SystemExit("setExecution block missing")
t = t.replace(old, new, 1)

helper = """
    /**
     * 已有过程备注时，若新内容不是其延续/缩短重写，则保留旧过程（避免定稿正文冲掉 .partial）。
     */
    private static boolean shouldKeepPriorExecution(String prev, String next) {
        if (!StringUtils.hasText(prev) || !StringUtils.hasText(next)) return false;
        String p = prev.trim();
        String n = next.trim();
        if (p.equals(n)) return false;
        if (n.startsWith(p) || p.startsWith(n)) return false;
        // 共享足够前缀视为同一次流式改写
        int share = 0;
        int lim = Math.min(64, Math.min(p.length(), n.length()));
        while (share < lim && p.charAt(share) == n.charAt(share)) share++;
        if (share >= 24) return false;
        // 已有实质过程，新内容完全换题 → 丢弃本次写入
        return p.length() >= 20;
    }

"""

if "shouldKeepPriorExecution" not in t:
    anchor = "    /** 过程痕迹须与最终气泡正文明显不同，否则视为重复丢弃 */"
    if anchor not in t:
        raise SystemExit("distinctTrace anchor missing")
    t = t.replace(anchor, helper + anchor, 1)

p.write_text(t, encoding="utf-8")
print("ok")
