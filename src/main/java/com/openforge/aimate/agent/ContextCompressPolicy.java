package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.ContextUsageDto;
import com.openforge.aimate.domain.SessionMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-factor context-compress eligibility.
 *
 * <p>Factors:
 * <ul>
 *   <li>Token pressure (used/limit) — drives urgency and how aggressively we shrink the keep window</li>
 *   <li>Foldable message count after keep-recent + pins — structural feasibility</li>
 *   <li>Dialogue share of usage — whether folding chat actually helps</li>
 * </ul>
 *
 * <p>High pressure with few long turns must still be compressible (adaptive keep),
 * unlike a fixed "need 20+ messages" gate.
 */
public final class ContextCompressPolicy {

    public enum Band {
        NORMAL,
        HIGH,
        CRITICAL,
        OVER
    }

    public record Plan(
            Band band,
            int keepFloor,
            int minFoldable,
            int keepRecent,
            int foldableCount,
            int pinnedInOld,
            boolean available,
            boolean recommended,
            String urgency,
            String hint
    ) {}

    private ContextCompressPolicy() {}

    public static Band bandOf(ContextUsageDto usage) {
        if (usage == null || usage.limitTokens() <= 0) return Band.NORMAL;
        double ratio = usage.usedTokens() * 1.0 / usage.limitTokens();
        if (ratio >= 1.0) return Band.OVER;
        if (ratio >= ContextUsageDto.COMPRESS_URGENT_RATIO) return Band.CRITICAL;
        if (ratio >= ContextUsageDto.COMPRESS_SUGGEST_RATIO) return Band.HIGH;
        return Band.NORMAL;
    }

    public static int keepFloor(Band band) {
        return switch (band) {
            case OVER -> 4;
            case CRITICAL -> 6;
            case HIGH -> 8;
            case NORMAL -> 14;
        };
    }

    public static int minFoldable(Band band) {
        return switch (band) {
            case OVER -> 2;
            case CRITICAL -> 3;
            case HIGH -> 4;
            case NORMAL -> 6;
        };
    }

    public static String urgencyOf(Band band) {
        return switch (band) {
            case OVER, CRITICAL -> "urgent";
            case HIGH -> "suggest";
            case NORMAL -> "none";
        };
    }

    /**
     * Keep recent window: at least {@code keepFloor}, and try to cover recent tool rounds.
     */
    public static int computeKeepRecentCount(List<SessionMessage> rest, int keepFloor, int keepToolRounds) {
        if (rest == null || rest.isEmpty()) return keepFloor;
        int n = rest.size();
        int rounds = 0;
        int i = n - 1;
        while (i >= 0 && rounds < keepToolRounds) {
            SessionMessage m = rest.get(i);
            if ("tool".equalsIgnoreCase(m.getRole()) || m.getReplyToMessageId() != null) {
                while (i >= 0) {
                    SessionMessage cur = rest.get(i);
                    if ("assistant".equalsIgnoreCase(cur.getRole())
                            && cur.getToolCallsJson() != null
                            && !cur.getToolCallsJson().isBlank()
                            && cur.getReplyToMessageId() == null) {
                        rounds++;
                        i--;
                        break;
                    }
                    i--;
                }
            } else if ("assistant".equalsIgnoreCase(m.getRole()) || "user".equalsIgnoreCase(m.getRole())) {
                rounds++;
                i--;
            } else {
                i--;
            }
        }
        int fromToolRounds = n - 1 - i;
        return Math.max(keepFloor, Math.min(n, Math.max(fromToolRounds, keepFloor)));
    }

    /** Same adaptive floors for group chat (message count based). */
    public static int computeKeepRecentCountSimple(int activeSize, int keepFloor) {
        return Math.min(activeSize, keepFloor);
    }

    public static boolean dialogueHeavy(ContextUsageDto usage) {
        if (usage == null || usage.usedTokens() <= 0) return false;
        double share = usage.conversationTokens() * 1.0 / usage.usedTokens();
        return share >= 0.40
                || usage.conversationTokens() >= (int) (usage.limitTokens() * 0.45);
    }

    /**
     * Analyze non-system active messages and usage; does not mutate.
     */
    public static Plan analyze(
            List<SessionMessage> rest,
            ContextUsageDto usage,
            int keepToolRounds
    ) {
        Band band = bandOf(usage);
        int floor = keepFloor(band);
        int minFold = minFoldable(band);
        String urgency = urgencyOf(band);

        List<SessionMessage> msgs = rest != null ? rest : List.of();
        int keep = computeKeepRecentCount(msgs, floor, keepToolRounds);
        if (msgs.size() <= keep) {
            keep = Math.min(msgs.size(), floor);
        }

        List<SessionMessage> old = new ArrayList<>();
        if (msgs.size() > keep) {
            old.addAll(msgs.subList(0, msgs.size() - keep));
        }

        int pinned = 0;
        int foldable = 0;
        for (SessionMessage m : old) {
            if (m.isPinned()) pinned++;
            else foldable++;
        }

        // Desperate: over/critical and still have something foldable — allow below minFoldable down to 1
        int required = minFold;
        if ((band == Band.OVER || band == Band.CRITICAL) && foldable >= 1) {
            required = 1;
        }

        boolean available = foldable >= required;
        boolean pressure = band != Band.NORMAL;
        boolean heavy = dialogueHeavy(usage);
        boolean recommended = pressure && (available || heavy);

        String hint;
        if (available && pressure && heavy) {
            hint = band == Band.OVER || band == Band.CRITICAL
                    ? "\u4e0a\u4e0b\u6587\u5df2\u8fd1/\u8d85\u4e0a\u9650\uff0c\u5efa\u8bae\u7acb\u5373\u538b\u7f29\u65e9\u671f\u8f6e\u6b21"
                    : "\u5360\u7528\u8f83\u9ad8\uff0c\u538b\u7f29\u53ef\u91ca\u653e\u65e9\u671f\u5bf9\u8bdd\u4e3a\u7ed3\u6784\u5316\u72b6\u6001";
        } else if (available && pressure && !heavy) {
            hint = "\u5360\u7528\u8f83\u9ad8\uff0c\u4f46\u5bf9\u8bdd\u5360\u6bd4\u4e0d\u9ad8\uff1b\u538b\u7f29\u4ecd\u53ef\u8bd5\uff0c\u6536\u76ca\u53ef\u80fd\u6709\u9650";
            recommended = true;
        } else if (available && !pressure) {
            hint = "\u53ef\u624b\u52a8\u538b\u7f29\uff1b\u5f53\u524d\u5360\u7528\u4e0d\u9ad8\uff0c\u975e\u5fc5\u9700";
            recommended = false;
        } else if (!available && pressure && !heavy) {
            hint = "\u5360\u7528\u4e3b\u8981\u6765\u81ea\u7cfb\u7edf/\u5de5\u5177\u5b9a\u4e49\uff0c\u538b\u7f29\u5bf9\u8bdd\u6536\u76ca\u6709\u9650\uff1b\u4e14\u53ef\u6298\u6bb5\u843d\u4e0d\u8db3";
            recommended = false;
        } else if (!available && pressure) {
            if (pinned > 0 && foldable == 0) {
                hint = "\u53ef\u538b\u6bb5\u843d\u5747\u4e3a\u9489\u4f4f\u6d88\u606f\uff0c\u8bf7\u53d6\u6d88\u90e8\u5206\u9489\u4f4f\u6216\u7ee7\u7eed\u5bf9\u8bdd\u540e\u518d\u8bd5";
            } else {
                hint = "\u6d88\u606f\u591a\u5728\u8fd1\u671f\u4fdd\u7559\u7a97\u53e3\u5185\uff08\u4e3a\u4fdd\u5de5\u5177\u94fe\uff09\uff0c\u6682\u65e0\u6cd5\u5b89\u5168\u6298\u53e0\uff1b\u53ef\u7ee7\u7eed\u5bf9\u8bdd\u6216\u53d6\u6d88\u9489\u4f4f\u540e\u518d\u8bd5";
            }
            recommended = heavy; // still surface tip when dialogue is the problem but structure blocks
        } else {
            hint = "\u5bf9\u8bdd\u8f6e\u6b21\u5c1a\u5c11\u4e14\u5360\u7528\u4e0d\u9ad8\uff0c\u6682\u65e0\u9700\u538b\u7f29";
        }

        return new Plan(band, floor, required, keep, foldable, pinned,
                available, recommended, urgency, hint);
    }

    public static Plan analyzeGroup(int activeSize, int pinnedInOldEstimate, ContextUsageDto usage) {
        Band band = bandOf(usage);
        int floor = keepFloor(band);
        int minFold = minFoldable(band);
        String urgency = urgencyOf(band);
        int keep = computeKeepRecentCountSimple(activeSize, floor);
        int old = Math.max(0, activeSize - keep);
        int foldable = Math.max(0, old - Math.max(0, pinnedInOldEstimate));
        int required = minFold;
        if ((band == Band.OVER || band == Band.CRITICAL) && foldable >= 1) {
            required = 1;
        }
        boolean available = foldable >= required;
        boolean pressure = band != Band.NORMAL;
        boolean heavy = dialogueHeavy(usage);
        boolean recommended = pressure && (available || heavy);
        String hint;
        if (available && pressure) {
            hint = band == Band.OVER || band == Band.CRITICAL
                    ? "\u4e0a\u4e0b\u6587\u5df2\u8fd1/\u8d85\u4e0a\u9650\uff0c\u5efa\u8bae\u7acb\u5373\u538b\u7f29"
                    : "\u5360\u7528\u8f83\u9ad8\uff0c\u5efa\u8bae\u538b\u7f29\u65e9\u671f\u7fa4\u804a\u8f6e\u6b21";
        } else if (!available && pressure) {
            hint = "\u8fd1\u671f\u4fdd\u7559\u7a97\u53e3\u5185\u53ef\u6298\u6bb5\u843d\u4e0d\u8db3\uff0c\u8bf7\u7ee7\u7eed\u5bf9\u8bdd\u540e\u518d\u8bd5";
        } else if (available) {
            hint = "\u53ef\u624b\u52a8\u538b\u7f29\uff1b\u5f53\u524d\u5360\u7528\u4e0d\u9ad8";
            recommended = false;
        } else {
            hint = "\u5bf9\u8bdd\u8f6e\u6b21\u5c1a\u5c11\uff0c\u6682\u65e0\u9700\u538b\u7f29";
        }
        return new Plan(band, floor, required, keep, foldable, pinnedInOldEstimate,
                available, recommended, urgency, hint);
    }
}