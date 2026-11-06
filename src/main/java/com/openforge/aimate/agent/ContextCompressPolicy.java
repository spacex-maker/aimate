package com.openforge.aimate.agent;

import com.openforge.aimate.agent.compress.CompressPlan;
import com.openforge.aimate.agent.compress.CompressPolicyV1;
import com.openforge.aimate.agent.compress.IntelligentCompressArch;
import com.openforge.aimate.agent.dto.ContextUsageDto;
import com.openforge.aimate.domain.SessionMessage;

import java.util.List;

/**
 * @deprecated use {@link IntelligentCompressArch#activePolicy()} — ICA v1 entrypoint.
 * Nested {@link Plan}/{@link Band} kept for call-site compatibility.
 */
@Deprecated
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

    public static Plan analyze(List<SessionMessage> rest, ContextUsageDto usage, int keepToolRounds) {
        return toPlan(IntelligentCompressArch.activePolicy().analyze(rest, usage, keepToolRounds));
    }

    public static Plan analyzeGroup(int activeSize, int pinnedInOldEstimate, ContextUsageDto usage) {
        return toPlan(IntelligentCompressArch.activePolicy().analyzeGroup(activeSize, pinnedInOldEstimate, usage));
    }

    public static int computeKeepRecentCount(List<SessionMessage> rest, int keepFloor, int keepToolRounds) {
        return CompressPolicyV1.computeKeepRecentCount(rest, keepFloor, keepToolRounds);
    }

    public static int keepFloor(Band band) {
        return CompressPolicyV1.keepFloor(toPressure(band));
    }

    private static CompressPlan.PressureBand toPressure(Band band) {
        return switch (band) {
            case NORMAL -> CompressPlan.PressureBand.NORMAL;
            case HIGH -> CompressPlan.PressureBand.HIGH;
            case CRITICAL -> CompressPlan.PressureBand.CRITICAL;
            case OVER -> CompressPlan.PressureBand.OVER;
        };
    }

    private static Band fromPressure(CompressPlan.PressureBand band) {
        return switch (band) {
            case NORMAL -> Band.NORMAL;
            case HIGH -> Band.HIGH;
            case CRITICAL -> Band.CRITICAL;
            case OVER -> Band.OVER;
        };
    }

    private static Plan toPlan(CompressPlan p) {
        if (p == null) {
            return new Plan(Band.NORMAL, 14, 6, 14, 0, 0, false, false, "none", "");
        }
        return new Plan(
                fromPressure(p.band()),
                p.keepFloor(),
                p.minFoldable(),
                p.keepRecent(),
                p.foldableCount(),
                p.pinnedInOld(),
                p.available(),
                p.recommended(),
                p.urgency(),
                p.hint()
        );
    }
}