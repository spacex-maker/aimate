package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.ContextUsageDto;
import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.llm.TokenCounter;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.repository.SystemModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 估算会话当前上下文占用（对齐 Cursor 右下角 context ring）。
 *
 * <p>权威来源是 {@code session_messages}（实际送模的上下文），
 * 不再依赖可能为空的 {@code agent_sessions.context_window} JSON。
 *
 * <p>分桶：系统提示 / 对话（含图片与思考）/ 工具定义。
 */
@Service
@RequiredArgsConstructor
public class ContextUsageService {

    public static final int DEFAULT_LIMIT = 128_000;

    /** 工具定义开销粗估（内置 + 检索工具 schema） */
    private static final int TOOLS_BASE_TOKENS = 1_800;

    /** 视觉模型单图粗估 token（常见 tile 近似） */
    private static final int TOKENS_PER_IMAGE = 765;

    private final SessionMessageService sessionMessageService;
    private final SystemModelRepository systemModelRepository;

    @Transactional(readOnly = true)
    public ContextUsageDto estimate(AgentSession session, Long systemModelId) {
        List<Message> context = sessionMessageService.load(session);
        int systemTokens = 0;
        List<Message> conversation = new ArrayList<>();
        for (Message m : context) {
            if (m == null) continue;
            if ("system".equalsIgnoreCase(m.role())) {
                systemTokens += TokenCounter.estimateTokensForMessages(List.of(m));
            } else {
                conversation.add(m);
            }
        }

        int conversationTokens = TokenCounter.estimateTokensForMessages(conversation);
        for (Message m : conversation) {
            if (m.reasoningContent() != null && !m.reasoningContent().isBlank()) {
                conversationTokens += TokenCounter.estimateTokens(m.reasoningContent());
            }
            if (m.imageDataUrls() != null && !m.imageDataUrls().isEmpty()) {
                conversationTokens += m.imageDataUrls().size() * TOKENS_PER_IMAGE;
            }
        }

        int toolsTokens = TOOLS_BASE_TOKENS;
        int limit = resolveLimit(systemModelId);
        return ContextUsageDto.of(systemTokens, conversationTokens, toolsTokens, limit);
    }

    public int resolveLimit(Long systemModelId) {
        if (systemModelId == null) return DEFAULT_LIMIT;
        return systemModelRepository.findById(systemModelId)
                .map(SystemModel::getContextWindowTokens)
                .filter(t -> t > 0)
                .orElse(DEFAULT_LIMIT);
    }
}
