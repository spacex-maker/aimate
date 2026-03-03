package com.openforge.aimate.llm;

import com.openforge.aimate.domain.LlmCallLog;
import com.openforge.aimate.llm.model.ChatResponse;
import com.openforge.aimate.repository.LlmCallLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Helper service to persist LLM call logs for cost/usage analytics.
 *
 * Kept intentionally lightweight: callers传入必要上下文（provider/model/userId/会话等），
 * 由本服务将 ChatResponse.usage / latency 等落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmCallLogService {

    private final LlmCallLogRepository repository;

    public void logSuccess(String provider,
                           String model,
                           LlmCallLog.CallType callType,
                           String toolName,
                           String endpoint,
                           Long userId,
                           String sessionId,
                           long latencyMs,
                           ChatResponse response) {
        try {
            LlmCallLog log = new LlmCallLog();
            log.setProvider(provider);
            log.setModel(model);
            log.setCallType(callType != null ? callType : LlmCallLog.CallType.OTHER);
            log.setToolName(toolName);
            log.setEndpoint(endpoint);
            log.setUserId(userId);
            log.setSessionId(sessionId);
            log.setLatencyMs(latencyMs);
            if (response != null && response.usage() != null) {
                log.setPromptTokens(response.usage().promptTokens());
                log.setCompletionTokens(response.usage().completionTokens());
                log.setTotalTokens(response.usage().totalTokens());
            }
            log.setHttpStatus(200);
            log.setSuccess(true);
            repository.save(log);
        } catch (Exception e) {
            log.warn("[LlmCallLog] Failed to persist success log: {}", e.getMessage());
        }
    }

    public void logError(String provider,
                         String model,
                         LlmCallLog.CallType callType,
                         String toolName,
                         String endpoint,
                         Long userId,
                         String sessionId,
                         long latencyMs,
                         Integer httpStatus,
                         String errorCode,
                         String errorMessage) {
        try {
            LlmCallLog log = new LlmCallLog();
            log.setProvider(provider);
            log.setModel(model);
            log.setCallType(callType != null ? callType : LlmCallLog.CallType.OTHER);
            log.setToolName(toolName);
            log.setEndpoint(endpoint);
            log.setUserId(userId);
            log.setSessionId(sessionId);
            log.setLatencyMs(latencyMs);
            log.setHttpStatus(httpStatus);
            log.setErrorCode(errorCode);
            log.setErrorMessage(errorMessage != null && errorMessage.length() > 500
                    ? errorMessage.substring(0, 500)
                    : errorMessage);
            log.setSuccess(false);
            repository.save(log);
        } catch (Exception e) {
            log.warn("[LlmCallLog] Failed to persist error log: {}", e.getMessage());
        }
    }
}

