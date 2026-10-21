package com.openforge.aimate.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record BatchAdminUsersResponse(
        @JsonProperty("succeeded") int succeeded,
        @JsonProperty("failed") int failed,
        @JsonProperty("errors") List<ItemError> errors
) {
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record ItemError(
            @JsonProperty("userId") long userId,
            @JsonProperty("message") String message
    ) {}
}
