package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.ContextUsageDto;
import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.llm.TokenCounter;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.repository.SystemModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 估算会话当前上下文占用，用于前端圆环指示器。
 */
@Service
@RequiredArgsConstructor
public class ContextUsageService {

    /** 无模型配置时的默认窗口 */
    public static final int DEFAULT_LIMIT = 128_000;

    /**
     * 工具定义开销粗估（JSON schema + 名称说明）。
     * 精确值随用户开关工具变化，此处取保守常数，避免低估。
     */
    private static final int TOOLS_OVERHEAD_TOKENS = 2_000;

    private final AgentContextService contextService;
    private final SystemModelRepository systemModelRepository;

    public ContextUsageDto estimate(AgentSession session, Long systemModelId) {
        List<Message> context = contextService.load(session);
        int messageTokens = TokenCounter.estimateTokensForMessages(context);
        int toolsTokens = context.isEmpty() ? 0 : TOOLS_OVERHEAD_TOKENS;
        int limit = resolveLimit(systemModelId);
        return ContextUsageDto.of(messageTokens, toolsTokens, limit);
    }

    public int resolveLimit(Long systemModelId) {
        if (systemModelId == null) return DEFAULT_LIMIT;
        return systemModelRepository.findById(systemModelId)
                .map(SystemModel::getContextWindowTokens)
                .filter(t -> t > 0)
                .orElse(DEFAULT_LIMIT);
    }
}
