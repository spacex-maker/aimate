package com.openforge.aimate.llm;

import com.openforge.aimate.billing.BillingSettingsService;
import com.openforge.aimate.billing.InsufficientPointsException;
import com.openforge.aimate.billing.ModelCostEstimator;
import com.openforge.aimate.billing.PointsService;
import com.openforge.aimate.domain.LlmCallLog;
import com.openforge.aimate.domain.PointLedger;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.llm.model.ChatResponse;
import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.repository.AgentSessionRepository;
import com.openforge.aimate.repository.LlmCallLogRepository;
import com.openforge.aimate.repository.SystemModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private final BillingSettingsService billingSettingsService;
    private final AgentSessionRepository agentSessionRepository;

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
                // 扣费 = ceil(API成本CNY × 加成倍率 × 每元积分)，默认 成本×4×1000
                long points = billingSettingsService.pointsForApiCostCny(costCny);
                if (points > 0) {
                    pointsService.deduct(
                            userId,
                            points,
                            PointLedger.CHAT_DEDUCT,
                            "LLM_CALL",
                            String.valueOf(row.getId()),
                            sessionId,
                            row.getId(),
                            // 用户可见备注：仅模型名，不暴露成本/倍率
                            model != null && !model.isBlank() ? model.trim() : "对话扣费",
                            resolveUserAgentId(sessionId)
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

    private Long resolveUserAgentId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) return null;
        return agentSessionRepository.findBySessionId(sessionId).map(AgentSession::getUserAgentId).orElse(null);
    }

    /**
     * 记录一次 Tavily 联网搜索并按计费规则扣积分（无 token，按次成本）。
     * 失败请求只记账不扣费；积分不足时抛 {@link InsufficientPointsException}。
     */
    public void logAndChargeWebSearch(Long userId,
                                      String sessionId,
                                      String searchDepth,
                                      long latencyMs,
                                      boolean success,
                                      String errorMessage) {
        LlmCallLog row;
        try {
            String depth = searchDepth == null || searchDepth.isBlank() ? "basic" : searchDepth.trim().toLowerCase();
            double costCny = success ? billingSettingsService.webSearchCostCny(depth) : 0;
            row = new LlmCallLog();
            row.setProvider("tavily");
            row.setModel("tavily-search-" + depth);
            row.setCallType(LlmCallLog.CallType.WEB_SEARCH);
            row.setToolName("tavily_search");
            row.setEndpoint("https://api.tavily.com/search");
            row.setUserId(userId);
            row.setSessionId(sessionId);
            row.setLatencyMs(latencyMs);
            row.setPromptTokens(0);
            row.setCompletionTokens(0);
            row.setTotalTokens(0);
            row.setEstimatedCostCny(costCny);
            row.setEstimatedCostUsd(costCny);
            row.setHttpStatus(success ? 200 : 500);
            row.setSuccess(success);
            if (!success && errorMessage != null) {
                row.setErrorMessage(errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage);
            }
            row = repository.save(row);

            if (success && userId != null && costCny > 0) {
                long points = billingSettingsService.pointsForApiCostCny(costCny);
                if (points > 0) {
                    pointsService.deduct(
                            userId,
                            points,
                            PointLedger.CHAT_DEDUCT,
                            "WEB_SEARCH",
                            String.valueOf(row.getId()),
                            sessionId,
                            row.getId(),
                            "联网搜索",
                            resolveUserAgentId(sessionId)
                    );
                    row.setPointsCharged(points);
                    repository.save(row);
                }
            }
        } catch (InsufficientPointsException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[LlmCallLog] Failed to persist web search log: {}", e.getMessage());
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
