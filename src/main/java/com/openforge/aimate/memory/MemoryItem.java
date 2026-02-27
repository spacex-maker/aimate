package com.openforge.aimate.memory;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Full memory entry returned by the browsing/management API.
 *
 * Includes the Milvus-generated {@code id} so that callers can delete
 * specific records via DELETE /api/memories/{id}.
 *
 * @param id            Milvus auto-generated INT64 primary key
 * @param sessionId     which agent session created this memory
 * @param content       the stored text
 * @param memoryType    EPISODIC / SEMANTIC / PROCEDURAL
 * @param importance    0.0 – 1.0
 * @param createTime    human-readable local time (yyyy-MM-dd HH:mm:ss)
 * @param score         similarity score — present only in search results, null in list results
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemoryItem(
        long       id,
        String     sessionId,
        String     content,
        MemoryType memoryType,
        float      importance,
        String     createTime,
        Double     score
) {}
