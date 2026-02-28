package com.openforge.aimate.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.apikey.UserApiKeyResolver;
import com.openforge.aimate.llm.LlmClient;
import com.openforge.aimate.llm.LlmProperties;
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
    private final UserApiKeyResolver keyResolver;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

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
        List<MemoryItem> current = memoryService.listMemories(null, null, null, 0, MAX_MEMORIES_FOR_COMPRESS, userId);
        if (current.isEmpty()) {
            return new CompressPrepareResult(List.of(), List.of(), null);
        }

        var config = keyResolver.resolveDefaultLlm(userId);
        if (config.isEmpty()) {
            return new CompressPrepareResult(current, List.of(), "请先配置默认 LLM 密钥");
        }

        StringBuilder sb = new StringBuilder();
        for (MemoryItem m : current) {
            sb.append("- [").append(m.memoryType()).append("] importance=").append(m.importance())
              .append(": ").append(m.content().length() > 200 ? m.content().substring(0, 200) + "..." : m.content()).append("\n");
        }
        String userContent = PROMPT_TEMPLATE.formatted(sb.toString());

        try {
            LlmClient client = new LlmClient(httpClient, objectMapper, config.get());
            ChatResponse response = client.chat(ChatRequest.simple(config.get().model(),
                    List.of(
                            Message.system("You output only valid JSON arrays. No markdown, no code fence."),
                            Message.user(userContent)
                    )));
            String raw = response.firstMessage().content();
            if (raw == null || raw.isBlank()) {
                return new CompressPrepareResult(current, List.of(), "LLM 返回为空");
            }
            raw = stripMarkdownJson(raw);
            List<CompressedMemoryDto> proposed = objectMapper.readValue(raw, new TypeReference<>() {});
            if (proposed == null) proposed = List.of();
            return new CompressPrepareResult(current, proposed, null);
        } catch (Exception e) {
            log.warn("[Compress] LLM prepare failed: {}", e.getMessage());
            return new CompressPrepareResult(current, List.of(), "压缩建议生成失败: " + e.getMessage());
        }
    }

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private static String stripMarkdownJson(String raw) {
        var m = JSON_BLOCK.matcher(raw);
        if (m.find()) return m.group(1).trim();
        return raw.trim();
    }

    /**
     * Deletes given memory IDs and inserts the new compressed memories (all for the given user).
     */
    public void executeCompression(@Nullable Long userId, List<String> deleteIds, List<CompressedMemoryDto> newMemories) {
        if (userId == null) return;
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
