package com.openforge.aimate.llm;

import com.openforge.aimate.billing.InsufficientPointsException;
import com.openforge.aimate.billing.ModelCostEstimator;
import com.openforge.aimate.billing.PointsService;
import com.openforge.aimate.domain.LlmCallLog;
import com.openforge.aimate.domain.PointLedger;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.llm.model.ChatResponse;
import com.openforge.aimate.repository.LlmCallLogRepository;
import com.openforge.aimate.repository.SystemModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Persist LLM call logs + 系统成本估算 + Chat 积分扣费。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmCallLogService {

    private final LlmCallLogRepository repository;
    private final SystemModelRepository systemModelRepository;
    private final PointsService pointsService;

    public void logSuccess(String provider,
                           String model,
                           LlmCallLog.CallType callType,
                           String toolName,
                           String endpoint,
                           Long userId,
                           String sessionId,
                           long latencyMs,
                           ChatResponse response) {
        LlmCallLog row;
        try {
            row = new LlmCallLog();
            row.setProvider(provider);
            row.setModel(model);
            row.setCallType(callType != null ? callType : LlmCallLog.CallType.OTHER);
            row.setToolName(toolName);
            row.setEndpoint(endpoint);
            row.setUserId(userId);
            row.setSessionId(sessionId);
            row.setLatencyMs(latencyMs);
            int prompt = 0;
            int completion = 0;
            if (response != null && response.usage() != null) {
                prompt = Math.max(0, response.usage().promptTokens());
                completion = Math.max(0, response.usage().completionTokens());
                row.setPromptTokens(prompt);
                row.setCompletionTokens(completion);
                row.setTotalTokens(response.usage().totalTokens());
            }
            SystemModel sm = resolveModel(model);
            double costCny = ModelCostEstimator.estimateCny(sm, prompt, completion);
            row.setEstimatedCostCny(costCny);
            // 兼容旧汇总字段：用 CNY 写入（管理端改为展示 CNY）
            row.setEstimatedCostUsd(costCny);
            row.setHttpStatus(200);
            row.setSuccess(true);
            row = repository.save(row);

            if (userId != null && (prompt > 0 || completion > 0)
                    && callType != LlmCallLog.CallType.MEMORY_COMPRESS) {
                long points = ModelCostEstimator.pointsForTokens(sm, prompt, completion);
                if (points > 0) {
                    pointsService.deduct(
                            userId,
                            points,
                            PointLedger.CHAT_DEDUCT,
                            "LLM_CALL",
                            String.valueOf(row.getId()),
                            sessionId,
                            row.getId(),
                            model + " · " + points + " 积分"
                    );
                    row.setPointsCharged(points);
                    repository.save(row);
                }
            }
        } catch (InsufficientPointsException e) {
            throw e;
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
            LlmCallLog row = new LlmCallLog();
            row.setProvider(provider);
            row.setModel(model);
            row.setCallType(callType != null ? callType : LlmCallLog.CallType.OTHER);
            row.setToolName(toolName);
            row.setEndpoint(endpoint);
            row.setUserId(userId);
            row.setSessionId(sessionId);
            row.setLatencyMs(latencyMs);
            row.setHttpStatus(httpStatus);
            row.setErrorCode(errorCode);
            row.setErrorMessage(errorMessage != null && errorMessage.length() > 500
                    ? errorMessage.substring(0, 500)
                    : errorMessage);
            row.setSuccess(false);
            repository.save(row);
        } catch (Exception e) {
            log.warn("[LlmCallLog] Failed to persist error log: {}", e.getMessage());
        }
    }

    private SystemModel resolveModel(String modelId) {
        if (modelId == null || modelId.isBlank()) return null;
        return systemModelRepository.findFirstByModelId(modelId).orElse(null);
    }
}
