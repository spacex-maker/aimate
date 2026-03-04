package com.openforge.aimate.memory.dto;

import com.openforge.aimate.memory.MemoryCompressService;

import java.util.List;

/** Request body for POST /api/memories/compress/execute. */
public record ExecuteCompressRequest(
        List<String>                                    delete_ids,
        List<MemoryCompressService.CompressedMemoryDto> new_memories,
        /** When non-empty: compress only these memory IDs (re-run LLM for subset); delete_ids/new_memories are ignored. */
        List<String>                                    include_ids
) {
    public ExecuteCompressRequest(List<String> delete_ids, List<MemoryCompressService.CompressedMemoryDto> new_memories) {
        this(delete_ids, new_memories, null);
    }
}
