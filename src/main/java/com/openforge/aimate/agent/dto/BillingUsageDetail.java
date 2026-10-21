package com.openforge.aimate.agent.dto;

public record BillingUsageDetail(
        Long id,
        String provider,
        String model,
        String callType,
        String sessionId,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Double estimatedCostCny,
        Long pointsCharged,
        boolean success,
        String createTime
) {}
