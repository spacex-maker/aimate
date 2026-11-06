package com.openforge.aimate.agent.compress;

/** Result of ICA policy analysis for one session/group snapshot. */
public record CompressPlan(
        PressureBand band,
        int keepFloor,
        int minFoldable,
        int keepRecent,
        int foldableCount,
        int pinnedInOld,
        boolean available,
        boolean recommended,
        String urgency,
        String hint,
        String policyId,
        String architectureId,
        String architectureVersion
) {
    public enum PressureBand {
        NORMAL,
        HIGH,
        CRITICAL,
        OVER
    }
}