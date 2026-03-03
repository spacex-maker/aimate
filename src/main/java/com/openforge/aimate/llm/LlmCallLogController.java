package com.openforge.aimate.llm;

import com.openforge.aimate.domain.LlmCallLog;
import com.openforge.aimate.repository.LlmCallLogRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only API for querying LLM call logs per user.
 *
 * Base path: /api/users/{userId}/llm-calls
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/{userId}/llm-calls")
public class LlmCallLogController {

    private final LlmCallLogRepository repo;

    public record LlmCallLogItem(
            Long   id,
            String provider,
            String model,
            String callType,
            String toolName,
            String endpoint,
            Long   userId,
            String sessionId,
            Long   latencyMs,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Integer httpStatus,
            Boolean success,
            String  createTime
    ) {}

    public record PageResponse(
            List<LlmCallLogItem> items,
            long  total,
            int   page,
            int   size
    ) {}

    @GetMapping
    public ResponseEntity<PageResponse> list(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<LlmCallLog> p = repo.findByUserIdOrderByCreateTimeDesc(userId, pr);

        List<LlmCallLogItem> items = p.getContent().stream()
                .map(this::toItem)
                .toList();

        return ResponseEntity.ok(new PageResponse(items, p.getTotalElements(), page, size));
    }

    private LlmCallLogItem toItem(LlmCallLog l) {
        LocalDateTime ct = l.getCreateTime();
        String ctStr = ct != null ? ct.toString() : null;
        return new LlmCallLogItem(
                l.getId(),
                l.getProvider(),
                l.getModel(),
                l.getCallType() != null ? l.getCallType().name() : null,
                l.getToolName(),
                l.getEndpoint(),
                l.getUserId(),
                l.getSessionId(),
                l.getLatencyMs(),
                l.getPromptTokens(),
                l.getCompletionTokens(),
                l.getTotalTokens(),
                l.getHttpStatus(),
                l.isSuccess(),
                ctStr
        );
    }
}

