# -*- coding: utf-8 -*-
from pathlib import Path

p = Path(r"f:\dev\proj2\aimate-connector\src\main\cdpCapture.ts")
t = p.read_text(encoding="utf-8")

start = t.find("const EXTRACT_JS = `")
end = t.find("})()`", start)
if start < 0 or end < 0:
    raise SystemExit("EXTRACT_JS bounds missing")
end = end + len("})()`")

NEW = r"""const EXTRACT_JS = `(() => {
  const stop = document.querySelector(
    'button[aria-label*="Stop" i], button[title*="Stop" i], [aria-label*="Stop generating" i]'
  );
  const generating = !!(stop && stop.offsetParent !== null);
  const mark = '\u3010\u6765\u81ea Aimate \u7fa4\u804a';
  const blocks = Array.from(document.querySelectorAll(
    '[data-message-role="assistant"], [class*="assistant"], [class*="anysphere-markdown"], .markdown-body, [class*="composer"]'
  ));
  let text = '';
  let bestEl = null;
  for (let bi = blocks.length - 1; bi >= 0; bi--) {
    const tx = (blocks[bi].innerText || '').trim();
    if (tx.length > 40 && !tx.includes(mark)) {
      text = tx;
      bestEl = blocks[bi];
      break;
    }
  }
  // Cursor \u771f\u601d\u8003\uff1aThought / Thinking \u6298\u53e0\u533a\uff08\u4e0e\u56de\u590d\u6b63\u6587\u5206\u79bb\uff09
  let thinking = '';
  const thinkSel = [
    '[data-testid*="thinking" i]',
    '[class*="thinking" i]',
    '[class*="reasoning" i]',
    '[class*="Thought" i]',
    '[class*="thought" i]',
    '[aria-label*="Thinking" i]',
    '[aria-label*="Thought" i]',
    '[aria-label*="Reasoning" i]',
    'div[class*="anysphere"] details',
    'details'
  ].join(', ');
  const roots = [];
  if (bestEl) roots.push(bestEl);
  roots.push(document.body);
  for (const root of roots) {
    if (!root) continue;
    const nodes = Array.from(root.querySelectorAll(thinkSel));
    for (const el of nodes) {
      if (!el || el.offsetParent === null) continue;
      const label = ((el.getAttribute && (el.getAttribute('aria-label') || '')) + ' ' + (el.innerText || '')).slice(0, 80);
      const looksThink = /think|thought|reason|\u601d\u8003|\u63a8\u7406/i.test(label)
        || (el.tagName === 'DETAILS' && /think|thought|reason|\u601d\u8003/i.test(el.querySelector('summary')?.innerText || ''));
      if (!looksThink && !(el.className || '').toString().match(/think|reason|Thought/i)) continue;
      let tx = '';
      if (el.tagName === 'DETAILS') {
        const clone = el.cloneNode(true);
        const sum = clone.querySelector && clone.querySelector('summary');
        if (sum) sum.remove();
        tx = (clone.innerText || '').trim();
      } else {
        tx = (el.innerText || '').trim();
      }
      // \u53bb\u6389\u6807\u9898\u884c
      tx = tx.replace(/^(Thinking|Thought|Reasoning|\u601d\u8003|\u63a8\u7406)[^\n]{0,40}\n+/i, '').trim();
      if (tx.length < 20) continue;
      if (tx.length > thinking.length) thinking = tx;
    }
    if (thinking) break;
  }
  // \u6309\u94ae\u300cThought for N\u300d\uff1a\u53d6\u5176\u540e\u7eed\u5144\u5f1f\u533a\u5757
  if (!thinking) {
    const scope = bestEl || document.body;
    const btns = Array.from((scope || document).querySelectorAll('button, [role="button"], span, div'));
    for (const el of btns) {
      const lab = (el.innerText || '').trim();
      if (!/^(Thought for|Thinking|Thought|Thinking for|\u601d\u8003)/i.test(lab)) continue;
      if (lab.length > 80) continue;
      let sib = el.nextElementSibling;
      for (let k = 0; k < 4 && sib; k++) {
        const tx = (sib.innerText || '').trim();
        if (tx.length >= 20 && tx !== lab) {
          thinking = tx;
          break;
        }
        sib = sib.nextElementSibling;
      }
      if (!thinking && el.parentElement) {
        const kids = Array.from(el.parentElement.children);
        const ix = kids.indexOf(el);
        for (let k = ix + 1; k < Math.min(kids.length, ix + 5); k++) {
          const tx = (kids[k].innerText || '').trim();
          if (tx.length >= 20 && tx !== lab) {
            thinking = tx;
            break;
          }
        }
      }
      if (thinking) break;
    }
  }
  let answer = text;
  if (thinking && text && text.includes(thinking)) {
    answer = text.split(thinking).join('').trim();
  }
  return {
    generating,
    text: (text || '').slice(0, 12000),
    thinking: (thinking || '').slice(0, 12000),
    answer: (answer || text || '').slice(0, 12000)
  };
})()`"""

t = t[:start] + NEW + t[end:]

# Only push thinking via onPartial; answer is for completion detection only
t = t.replace(
    "      if (thinking) await pushThrottled(thinking, 'thinking', 't')\n"
    "      if (text) await pushThrottled(text, 'progress', 'a')\n",
    "      // \u53ea\u63a8 Cursor \u771f\u601d\u8003\uff1b\u56de\u590d\u6b63\u6587\u4e0d\u8d70 progress\uff08\u907f\u514d\u4e0e\u6c14\u6ce1\u91cd\u590d\uff09\n"
    "      if (thinking) await pushThrottled(thinking, 'thinking', 't')\n",
)

t = t.replace(
    """        if (onPartial && thinking && thinking !== lastPushedThinking) {
          try {
            await onPartial(thinking, 'thinking')
          } catch {
            /* ignore */
          }
        }
        if (onPartial && text && text !== lastPushed) {
          try {
            await onPartial(text, 'progress')
          } catch {
            /* ignore */
          }
        }
        return { ok: true, text }""",
    """        if (onPartial && thinking && thinking !== lastPushedThinking) {
          try {
            await onPartial(thinking, 'thinking')
          } catch {
            /* ignore */
          }
        }
        return { ok: true, text }""",
)

# timeout tail: don't push answer as progress
t = t.replace(
    """    if (lastText.length >= minLen) {
      if (onPartial && lastPushedThinking) {
        try {
          await onPartial(lastPushedThinking, 'thinking')
        } catch {
          /* ignore */
        }
      }
      if (onPartial && lastText !== lastPushed) {
        try {
          await onPartial(lastText, 'progress')
        } catch {
          /* ignore */
        }
      }
      return { ok: true, text: lastText }
    }""",
    """    if (lastText.length >= minLen) {
      if (onPartial && lastPushedThinking) {
        try {
          await onPartial(lastPushedThinking, 'thinking')
        } catch {
          /* ignore */
        }
      }
      return { ok: true, text: lastText }
    }""",
)

p.write_text(t, encoding="utf-8")
print("cdpCapture think-only ok")
