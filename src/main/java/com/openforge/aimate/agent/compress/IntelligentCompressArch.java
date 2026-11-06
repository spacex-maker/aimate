package com.openforge.aimate.agent.compress;

/**
 * Aimate Intelligent Compress Architecture (ICA).
 *
 * <p>Product-facing name: {@link #DISPLAY_NAME_ZH} / {@link #DISPLAY_NAME_EN}.
 * Bump {@link #VERSION} when the layered pipeline or public contract changes;
 * bump {@link #POLICY_ID} when eligibility thresholds / factors change without a full arch bump.
 *
 * <h2>Layers (v1)</h2>
 * <ol>
 *   <li>L0 — Deterministic prune for summarizer draft</li>
 *   <li>L1 — Structured SessionState JSON rollup</li>
 *   <li>L2 — Write-before-compact to Soul (when memory on)</li>
 *   <li>L3 — Exclude aged turns; pins + recent verbatim kept</li>
 *   <li>P  — Multi-factor policy (pressure / foldable / dialogue share / adaptive keep)</li>
 * </ol>
 *
 * <p>Iterate by adding {@code CompressPolicyV2} and switching {@link #activePolicy()}.
 */
public final class IntelligentCompressArch {

    public static final String ID = "aimate-ica";
    public static final String DISPLAY_NAME_ZH = "\u667a\u80fd\u538b\u7f29";
    public static final String DISPLAY_NAME_EN = "Intelligent Compress Architecture";
    /** Semver of the architecture contract (layers + API fields). */
    public static final String VERSION = "1.0.0";
    /** Active eligibility / keep-window policy id. */
    public static final String POLICY_ID = "ica-policy-v1";

    private IntelligentCompressArch() {}

    public static CompressPolicyEngine activePolicy() {
        return CompressPolicyV1.INSTANCE;
    }

    public static String versionLabel() {
        return "v" + VERSION;
    }

    public static String marketingLineZh() {
        return DISPLAY_NAME_ZH + " " + versionLabel();
    }
}