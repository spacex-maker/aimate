package com.openforge.aimate.memory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for browsing and managing the Agent's long-term memory.
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Endpoint                              Description              │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  GET  /api/memories                    分页列举所有记忆            │
 * │  GET  /api/memories/search?q=xxx       语义搜索（向量相似度）      │
 * │  GET  /api/memories/count              统计记忆总数               │
 * │  POST /api/memories                    手动添加一条记忆            │
 * │  DELETE /api/memories/{id}             按 ID 删除单条记忆          │
 * │  DELETE /api/memories/session/{sid}    删除某会话的全部记忆         │
 * │  DELETE /api/memories/type/{type}      删除某类型的全部记忆         │
 * └─────────────────────────────────────────────────────────────────┘
 */
@RestController
@RequestMapping("/api/memories")
@RequiredArgsConstructor
public class MemoryController {

    private final LongTermMemoryService memoryService;
    private final EmbeddingClient       embeddingClient;

    // ── List ─────────────────────────────────────────────────────────────────

    /**
     * Paginated list of memories with optional filters.
     *
     * Query params:
     *   type      — EPISODIC | SEMANTIC | PROCEDURAL  (optional)
     *   session   — filter by sessionId                (optional)
     *   keyword   — substring match in content         (optional)
     *   page      — 0-based page number               (default 0)
     *   size      — records per page, max 100         (default 20)
     */
    @GetMapping
    public ResponseEntity<MemoryPage> listMemories(
            @RequestParam(required = false) MemoryType type,
            @RequestParam(required = false) String session,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        long offset = (long) page * size;
        List<MemoryItem> items = memoryService.listMemories(type, session, keyword, offset, size);
        long total = memoryService.countMemories(type, session);

        return ResponseEntity.ok(new MemoryPage(items, total, page, size));
    }

    // ── Semantic search ───────────────────────────────────────────────────────

    /**
     * Semantic (vector) search across all memories.
     *
     * Unlike the Agent's internal recall(), this endpoint has NO score threshold
     * so you can see all results ranked by relevance.
     *
     * Query params:
     *   q      — search query text (required)
     *   topK   — max results       (default 10, max 50)
     */
    @GetMapping("/search")
    public ResponseEntity<List<MemoryItem>> searchMemories(
            @RequestParam @NotBlank String q,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int topK) {

        List<MemoryItem> results = memoryService.searchMemories(q, topK);
        return ResponseEntity.ok(results);
    }

    // ── Count ─────────────────────────────────────────────────────────────────

    /**
     * Count total memories, optionally filtered by type and/or session.
     */
    @GetMapping("/count")
    public ResponseEntity<CountResponse> countMemories(
            @RequestParam(required = false) MemoryType type,
            @RequestParam(required = false) String session) {

        long count = memoryService.countMemories(type, session);
        return ResponseEntity.ok(new CountResponse(count, type, session));
    }

    // ── Manual add ───────────────────────────────────────────────────────────

    /**
     * Manually add a memory.
     * Useful for seeding domain knowledge before the Agent has run any sessions.
     */
    @PostMapping
    public ResponseEntity<Void> addMemory(@Valid @RequestBody AddMemoryRequest req) {
        memoryService.remember(
                req.sessionId() != null ? req.sessionId() : "manual",
                req.content(),
                req.memoryType() != null ? req.memoryType() : MemoryType.SEMANTIC,
                req.importance() != null ? req.importance() : 0.8f
        );
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    /** Delete a single memory by its Milvus ID. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable long id) {
        memoryService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Delete all memories produced by a specific session. */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> deleteBySession(@PathVariable String sessionId) {
        memoryService.deleteBySession(sessionId);
        return ResponseEntity.noContent().build();
    }

    /** Delete all memories of a given type. */
    @DeleteMapping("/type/{type}")
    public ResponseEntity<Void> deleteByType(@PathVariable MemoryType type) {
        memoryService.deleteByType(type);
        return ResponseEntity.noContent().build();
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────────

    public record MemoryPage(
            List<MemoryItem> items,
            long             total,
            int              page,
            int              size
    ) {}

    public record CountResponse(
            long       count,
            MemoryType type,
            String     session
    ) {}

    public record AddMemoryRequest(
            @NotBlank @Size(max = 4000) String content,
            MemoryType memoryType,
            String     sessionId,
            Float      importance
    ) {}
}
