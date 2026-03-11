package com.openforge.aimate.memory;

import com.openforge.aimate.memory.dto.AddMemoryRequest;
import com.openforge.aimate.memory.dto.CountResponse;
import com.openforge.aimate.memory.dto.ExecuteCompressRequest;
import com.openforge.aimate.memory.dto.MetaResponse;
import com.openforge.aimate.memory.dto.MigrationStatusResponse;
import com.openforge.aimate.memory.dto.MemoryPage;
import com.openforge.aimate.memory.dto.StartMigrateResponse;
import com.openforge.aimate.memory.dto.UpdateImportanceRequest;
import com.openforge.aimate.memory.dto.UpdateNoCompressRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
 * │  DELETE /api/memories/clear            清空当前用户全部记忆         │
 * │  POST   /api/memories/recreate-collection  重建当前用户的集合（含 user_id） │
 * └─────────────────────────────────────────────────────────────────┘
 */
@RestController
@RequestMapping("/api/memories")
@RequiredArgsConstructor
public class MemoryController {

    private final LongTermMemoryService  memoryService;
    private final MemoryCompressService  compressService;
    private final MemoryMigrationService migrationService;
    private final MigrationStateHolder   migrationStateHolder;

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        return null;
    }

    // ── List ─────────────────────────────────────────────────────────────────

    /**
     * Paginated list of memories with optional filters.
     * Uses the current user's configured embedding collection.
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
        Long userId = currentUserId();

        List<MemoryItem> items = memoryService.listMemories(type, session, keyword, offset, size, userId);
        long total = memoryService.countMemories(type, session, userId);

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

        List<MemoryItem> results = memoryService.searchMemories(q, topK, currentUserId());
        return ResponseEntity.ok(results);
    }

    // ── Count ─────────────────────────────────────────────────────────────────

    /**
     * Count total memories, optionally filtered by type and/or session.
     * Uses the current user's configured collection.
     */
    @GetMapping("/count")
    public ResponseEntity<CountResponse> countMemories(
            @RequestParam(required = false) MemoryType type,
            @RequestParam(required = false) String session) {

        long count = memoryService.countMemories(type, session, currentUserId());
        return ResponseEntity.ok(new CountResponse(count, type, session));
    }

    // ── Meta ──────────────────────────────────────────────────────────────────

    /**
     * 返回当前用户长期记忆使用的 Milvus Collection 名称，便于前端展示和排查问题。
     */
    @GetMapping("/meta")
    public ResponseEntity<MetaResponse> meta() {
        Long userId = currentUserId();
        String collection = memoryService.resolveCollectionName(userId);
        return ResponseEntity.ok(new MetaResponse(collection));
    }

    /**
     * 将当前用户所有会话记录重新写入「当前默认向量模型」对应的 Collection」。
     * 仅追加，不会删除旧 Collection 中的记录；进度通过
     * /topic/memory-migration/{userId} WebSocket 事件推送。
     */
    @PostMapping("/migrate-to-current-collection")
    public ResponseEntity<StartMigrateResponse> migrateToCurrentCollection() {
        Long userId = currentUserId();
        migrationService.startMigration(userId);
        return ResponseEntity.accepted().body(new StartMigrateResponse(true));
    }

    /** 获取当前用户同步进度（刷新页面后可调用以恢复显示）. */
    @GetMapping("/migration-status")
    public ResponseEntity<MigrationStatusResponse> getMigrationStatus() {
        Long userId = currentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var snapshot = migrationStateHolder.get(userId);
        if (snapshot == null) {
            return ResponseEntity.ok(MigrationStatusResponse.idle());
        }
        return ResponseEntity.ok(new MigrationStatusResponse(
                snapshot.status(),
                snapshot.totalSessions(),
                snapshot.processedSessions(),
                snapshot.writtenMemories(),
                snapshot.currentTask(),
                snapshot.error(),
                snapshot.stepLog() != null ? snapshot.stepLog() : List.of()
        ));
    }

    /** 请求中断当前用户的同步. */
    @PostMapping("/migration/cancel")
    public ResponseEntity<Map<String, String>> cancelMigration() {
        Long userId = currentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        migrationService.requestCancel(userId);
        return ResponseEntity.ok(Map.of("message", "已请求中断，同步将在当前步骤结束后停止"));
    }

    // ── Manual add ───────────────────────────────────────────────────────────

    /**
     * Manually add a memory to the current user's collection.
     */
    @PostMapping
    public ResponseEntity<Void> addMemory(@Valid @RequestBody AddMemoryRequest req) {
        memoryService.remember(
                req.sessionId() != null ? req.sessionId() : "manual",
                req.content(),
                req.memoryType() != null ? req.memoryType() : MemoryType.SEMANTIC,
                req.importance() != null ? req.importance() : 0.8f,
                currentUserId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    /** Recreate the current user's Milvus collection (drop + create with current schema including user_id). Use then 「同步对话到记忆」 to repopulate. */
    @PostMapping("/recreate-collection")
    public ResponseEntity<Map<String, Object>> recreateCollection() {
        Long userId = currentUserId();
        boolean ok = memoryService.recreateCollectionForUser(userId);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Milvus 不可用，无法重建集合"));
        }
        String name = memoryService.resolveCollectionName(userId);
        return ResponseEntity.ok(Map.of(
                "message", "集合已重建",
                "collectionName", name
        ));
    }

    /** Clear all long-term memories for the current user. Must be declared before /{id} so "clear" is not matched as id. */
    @DeleteMapping("/clear")
    public ResponseEntity<?> clearAllForCurrentUser() {
        Long userId = currentUserId();
        try {
            memoryService.deleteAllForUser(userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Delete all memories produced by a specific session (in current user's collection). */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> deleteBySession(@PathVariable String sessionId) {
        memoryService.deleteBySession(sessionId, currentUserId());
        return ResponseEntity.noContent().build();
    }

    /** Delete all memories of a given type (in current user's collection). */
    @DeleteMapping("/type/{type}")
    public ResponseEntity<Void> deleteByType(@PathVariable MemoryType type) {
        memoryService.deleteByType(type, currentUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Update importance of a single memory (current user only).
     * Body: { "importance": 0.0–1.0 }. Returns 404 if not found or not owner.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateImportance(
            @PathVariable long id,
            @RequestBody UpdateImportanceRequest body) {
        Long userId = currentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        float importance = body.importance() != null
                ? Math.max(0f, Math.min(1f, body.importance()))
                : 0.8f;
        boolean updated = memoryService.updateImportance(id, importance, userId);
        return updated ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /**
     * Mark a memory as protected from compression (or remove the protection).
     * Body: { "noCompress": true | false }.
     */
    @PatchMapping("/{id}/no-compress")
    public ResponseEntity<Void> updateNoCompress(
            @PathVariable long id,
            @RequestBody UpdateNoCompressRequest body) {
        Long userId = currentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean noCompress = Boolean.TRUE.equals(body.noCompress());
        // 仅更新「禁止压缩」标记，本质上是幂等操作：
        // 若记录已被删除或不存在，不再向前端暴露 404，而是视为“目标状态已达成”返回 204，避免打扰用户。
        memoryService.updateNoCompress(id, noCompress, userId);
        return ResponseEntity.noContent().build();
    }

    /** Delete a single memory by its Milvus ID (in current user's collection). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable long id) {
        memoryService.deleteById(id, currentUserId());
        return ResponseEntity.noContent().build();
    }

    // ── Compress (aggregate similar memories) ──────────────────────────────────

    /**
     * Prepare compression: fetch current memories and ask LLM for a merged list.
     * Returns current + proposed for UI comparison; user confirms then calls execute.
     */
    @PostMapping("/compress/prepare")
    public ResponseEntity<MemoryCompressService.CompressPrepareResult> prepareCompress() {
        return ResponseEntity.ok(compressService.prepareCompression(currentUserId()));
    }

    /**
     * Execute compression: delete given IDs and insert the new compressed memories.
     */
    @PostMapping("/compress/execute")
    public ResponseEntity<Void> executeCompress(@Valid @RequestBody ExecuteCompressRequest req) {
        compressService.executeCompression(currentUserId(), req);
        return ResponseEntity.noContent().build();
    }

}
