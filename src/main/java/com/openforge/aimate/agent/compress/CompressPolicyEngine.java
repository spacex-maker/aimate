package com.openforge.aimate.agent.compress;

import com.openforge.aimate.agent.dto.ContextUsageDto;
import com.openforge.aimate.domain.SessionMessage;

import java.util.List;

/** Pluggable compress eligibility engine for ICA policy iteration. */
public interface CompressPolicyEngine {

    String policyId();

    CompressPlan analyze(List<SessionMessage> rest, ContextUsageDto usage, int keepToolRounds);

    CompressPlan analyzeGroup(int activeSize, int pinnedInOldEstimate, ContextUsageDto usage);
}