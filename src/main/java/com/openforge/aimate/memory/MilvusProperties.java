package com.openforge.aimate.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connection parameters for the Milvus vector database.
 *
 * application.yml:
 *
 * agent:
 *   milvus:
 *     host: localhost
 *     port: 19530
 *     collection-name: agent_memories
 *     vector-dimensions: 1536
 */
@ConfigurationProperties(prefix = "agent.milvus")
public record MilvusProperties(
        @DefaultValue("localhost") String host,
        @DefaultValue("19530")     int    port,
        @DefaultValue("agent_memories") String collectionName,
        @DefaultValue("1536")      int    vectorDimensions
) {}
