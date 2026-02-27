package com.openforge.aimate.memory;

/**
 * A single long-term memory entry returned from a recall query.
 *
 * @param content       the stored memory text
 * @param memoryType    EPISODIC / SEMANTIC / PROCEDURAL
 * @param sourceSession the session that originally created this memory
 * @param importance    0.0 – 1.0; higher = more important
 * @param score         cosine similarity score from the vector search (0.0 – 1.0)
 */
public record MemoryRecord(
        String     content,
        MemoryType memoryType,
        String     sourceSession,
        float      importance,
        double     score
) {}
