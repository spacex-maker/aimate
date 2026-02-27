package com.openforge.aimate.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The "function" sub-object inside a Tool definition.
 *
 * "parameters" is typed as JsonNode so that the JSON Schema stored in
 * AgentTool.inputSchema can be deserialized and re-serialized verbatim —
 * no intermediate POJO mapping needed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolFunction(
        String name,
        String description,
        JsonNode parameters
) {}
