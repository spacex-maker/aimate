package com.openforge.aimate.memory;

import com.openforge.aimate.memory.dto.ExecuteCompressRequest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.apikey.UserApiKeyResolver;
import com.openforge.aimate.domain.LlmCallLog;
import com.openforge.aimate.llm.LlmCallLogService;
import com.openforge.aimate.llm.LlmClient;
import com.openforge.aimate.llm.LlmProperties;
import com.openforge.aimate.llm.LlmRouter;
import com.openforge.aimate.llm.model.ChatRequest;
import com.openforge.aimate.llm.model.ChatResponse;
import com.openforge.aimate.llm.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Prepares and executes long-term memory compression: merge duplicate/similar
 * memories via LLM, then replace with compressed set after user confirmation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryCompressService {

    private static final int MAX_MEMORIES_FOR_COMPRESS = 200;
    private static final String COMPRESS_SESSION_ID = "compressed";

    private final LongTermMemoryService memoryService;
    private final UserApiKeyResolver    keyResolver;
    private final LlmRouter             llmRouter;
    private final LlmCallLogService llmCallLogService;
    private final HttpClient            httpClient;
    private final ObjectMapper          objectMapper;

    private static final String PROMPT_TEMPLATE = """
        You are a memory compression assistant. Below is a list of long-term memory entries (content, type, importance).
        Merge duplicates and semantically similar items into a smaller set. Keep important facts; drop redundant or low-value entries.
        Preserve memory_type (SEMANTIC, EPISODIC, PROCEDURAL) and set importance 0.0-1.0.
        Reply with ONLY a JSON array, no markdown, no explanation. Example:
        [{"content":"用户是Java开发人员","memory_type":"SEMANTIC","importance":0.85},{"content":"...","memory_type":"EPISODIC","importance":0.7}]

        Memories to compress:
        %s
        """;

    /**
     * Fetches current memories and asks LLM for a compressed list. Returns both for UI comparison.
     */
    public CompressPrepareResult prepareCompression(@Nullable Long userId) {
        if (userId == null) {
            return new CompressPrepareResult(List.of(), List.of(), "未登录");
        }
        List<MemoryItem> all = memoryService.listMemories(null, null, null, 0, MAX_MEMORIES_FOR_COMPRESS, userId);
        // 跳过被标记为「禁止压缩」的记忆，避免重要记忆被合并/删除
        List<MemoryItem> current = new ArrayList<>();
        for (MemoryItem m : all) {
            if (!m.noCompress()) {
                current.add(m);
            }
        }
        if (current.isEmpty()) {
            return new CompressPrepareResult(List.of(), List.of(), null);
        }
        var proposedResult = callLlmForCompress(current, userId);
        return new CompressPrepareResult(current, proposedResult.proposed(), proposedResult.error());
    }

    /**
     * Calls LLM to merge the given memory list into a compressed list.
     */
    private CompressProposeResult callLlmForCompress(List<MemoryItem> current, @Nullable Long userId) {
        if (current.isEmpty()) return new CompressProposeResult(List.of(), null);
        StringBuilder sb = new StringBuilder();
        for (MemoryItem m : current) {
            sb.append("- [").append(m.memoryType()).append("] importance=").append(m.importance())
              .append(": ").append(m.content().length() > 200 ? m.content().substring(0, 200) + "..." : m.content()).append("\n");
        }
        String userContent = PROMPT_TEMPLATE.formatted(sb.toString());
        var userConfigOpt = keyResolver.resolveDefaultLlm(userId);
        long startNs = System.nanoTime();
        try {
            ChatResponse response;
            if (userConfigOpt.isPresent()) {
                LlmProperties.ProviderConfig cfg = userConfigOpt.get();
                LlmClient client = new LlmClient(httpClient, objectMapper, cfg);
                response = client.chat(ChatRequest.simple(cfg.model(),
                        List.of(
                                Message.system("You output only valid JSON arrays. No markdown, no code fence."),
                                Message.user(userContent)
                        )));
                long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
                llmCallLogService.logSuccess(
                        cfg.name(), cfg.model(), LlmCallLog.CallType.MEMORY_COMPRESS,
                        "compress_memory", "/v1/chat/completions", userId, null, latencyMs, response);
            } else {
                response = llmRouter.chat(ChatRequest.simple(null,
                        List.of(
                                Message.system("You output only valid JSON arrays. No markdown, no code fence."),
                                Message.user(userContent)
                        )));
                long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
                llmCallLogService.logSuccess(
                        "system_router", response.model(), LlmCallLog.CallType.MEMORY_COMPRESS,
                        "compress_memory", "/v1/chat/completions", userId, null, latencyMs, response);
            }
            String raw = response.firstMessage().content();
            if (raw == null || raw.isBlank()) return new CompressProposeResult(List.of(), "LLM 返回为空");
            raw = stripMarkdownJson(raw);
            List<CompressedMemoryDto> proposed = objectMapper.readValue(raw, new TypeReference<>() {});
            return new CompressProposeResult(proposed != null ? proposed : List.of(), null);
        } catch (Exception e) {
            log.warn("[Compress] LLM prepare failed: {}", e.getMessage());
            return new CompressProposeResult(List.of(), "压缩建议生成失败: " + e.getMessage());
        }
    }

    private record CompressProposeResult(List<CompressedMemoryDto> proposed, String error) {}

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private static String stripMarkdownJson(String raw) {
        var m = JSON_BLOCK.matcher(raw);
        if (m.find()) return m.group(1).trim();
        return raw.trim();
    }

    /**
     * Executes compression from the request: either (delete_ids + new_memories) or (include_ids only).
     * When include_ids is non-empty, loads those memories, re-runs LLM to get proposed for the subset, then deletes include_ids and inserts proposed.
     */
    public void executeCompression(@Nullable Long userId, ExecuteCompressRequest req) {
        if (userId == null) return;
        List<String> deleteIds;
        List<CompressedMemoryDto> newMemories;
        if (req.include_ids() != null && !req.include_ids().isEmpty()) {
            List<MemoryItem> subset = memoryService.listMemoriesByIds(req.include_ids(), userId);
            if (subset.isEmpty()) {
                log.warn("[Compress] include_ids provided but no memories found for user {}", userId);
                return;
            }
            CompressProposeResult proposed = callLlmForCompress(subset, userId);
            if (proposed.error() != null || proposed.proposed().isEmpty()) {
                log.warn("[Compress] Cannot execute with subset: {}", proposed.error());
                return;
            }
            deleteIds = req.include_ids();
            newMemories = proposed.proposed();
        } else {
            deleteIds = req.delete_ids() != null ? req.delete_ids() : List.of();
            newMemories = req.new_memories() != null ? req.new_memories() : List.of();
        }
        for (String id : deleteIds) {
            try {
                memoryService.deleteById(Long.parseLong(id), userId);
            } catch (Exception e) {
                log.warn("[Compress] Failed to delete memory id={}: {}", id, e.getMessage());
            }
        }
        for (CompressedMemoryDto dto : newMemories) {
            MemoryType type = dto.memory_type() != null ? MemoryType.valueOf(dto.memory_type().toUpperCase()) : MemoryType.SEMANTIC;
            float imp = dto.importance() != null ? dto.importance().floatValue() : 0.8f;
            if (dto.content() != null && !dto.content().isBlank()) {
                memoryService.remember(COMPRESS_SESSION_ID, dto.content(), type, imp, userId);
            }
        }
        log.info("[Compress] userId={} deleted {} inserted {}", userId, deleteIds.size(), newMemories.size());
    }

    public record CompressPrepareResult(
            List<MemoryItem> current,
            List<CompressedMemoryDto> proposed,
            String error
    ) {}

    public record CompressedMemoryDto(String content, String memory_type, Double importance) {}
}
