package com.openforge.aimate.agent.dto;

import java.util.List;

public record BillingUsageResponse(
        List<BillingUsageSummary> summaries,
        long grandTotalTokens,
        double grandTotalCostUsd
) {}
