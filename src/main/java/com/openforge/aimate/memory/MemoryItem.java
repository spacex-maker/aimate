package com.openforge.aimate.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Full memory entry returned by the browsing/management API.
 *
 * Includes the Milvus-generated {@code id} so that callers can delete
 * specific records via DELETE /api/memories/{id}. ID is serialized as string
 * so JS clients do not lose precision (JS Number is safe only to 2^53-1).
 *
 * @param id            Milvus auto-generated INT64 primary key (serialized as string in JSON)
 * @param sessionId     which agent session created this memory
 * @param content       the stored text
 * @param memoryType    EPISODIC / SEMANTIC / PROCEDURAL
 * @param importance    0.0 – 1.0
 * @param createTime    human-readable local time (yyyy-MM-dd HH:mm:ss)
 * @param score         similarity score — present only in search results, null in list results
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemoryItem(
        @JsonSerialize(using = ToStringSerializer.class) long id,
        String     sessionId,
        String     content,
        MemoryType memoryType,
        float      importance,
        String     createTime,
        Double     score
) {}
