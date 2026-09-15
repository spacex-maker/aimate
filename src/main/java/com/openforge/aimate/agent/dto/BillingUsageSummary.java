package com.openforge.aimate.agent.dto;

public record BillingUsageSummary(
        Long userId,
        String username,
        long callCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        double estimatedCostUsd
) {}
