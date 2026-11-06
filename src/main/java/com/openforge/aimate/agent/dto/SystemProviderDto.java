package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openforge.aimate.domain.SystemProvider;

public record SystemProviderDto(
        @JsonProperty("id") long id,
        @JsonProperty("code") String code,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("description") String description,
        @JsonProperty("enabled") boolean enabled,
        @JsonProperty("sortOrder") int sortOrder,
        @JsonProperty("apiKeySet") boolean apiKeySet,
        @JsonProperty("apiKeyMasked") String apiKeyMasked
) {
    public static SystemProviderDto from(SystemProvider p) {
        String raw = p.getApiKey();
        boolean set = raw != null && !raw.isBlank();
        return new SystemProviderDto(
                p.getId(),
                p.getCode(),
                p.getDisplayName(),
                p.getDescription(),
                p.isEnabled(),
                p.getSortOrder(),
                set,
                set ? mask(raw) : null
        );
    }

    private static String mask(String raw) {
        String t = raw.trim();
        if (t.length() <= 10) return t.charAt(0) + "***";
        return t.substring(0, Math.min(12, t.length())) + "...";
    }
}