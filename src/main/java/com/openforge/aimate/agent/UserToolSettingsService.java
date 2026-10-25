package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.ToolSettingsDto;
import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.domain.UserToolSettings;
import com.openforge.aimate.repository.AgentSessionRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import com.openforge.aimate.repository.UserToolSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统工具开关：用户级（管理端/兼容）+ Agent 默认 + 单聊会话覆盖。
 * <p>
 * 生效优先级：会话已覆盖 → Agent 默认 → 用户级（无 Agent 时）→ 全开。
 * Agent 默认变更时清除该 Agent 下所有单聊覆盖，使全部单聊跟随新默认。
 */
@Service
@RequiredArgsConstructor
public class UserToolSettingsService {

    private final UserToolSettingsRepository repository;
    private final UserAgentRepository userAgentRepository;
    private final AgentSessionRepository agentSessionRepository;

    /**
     * 获取用户工具设置，若不存在则返回 null（调用方按全开处理）。
     */
    @Transactional(readOnly = true)
    public UserToolSettings getOrCreate(Long userId) {
        if (userId == null) return defaultSettings();
        return repository.findByUserId(userId).orElse(null);
    }

    /** 若 getOrCreate 返回 null（无记录），可调用此方法在独立事务中创建默认并返回。 */
    @Transactional
    public UserToolSettings ensureAndGet(Long userId) {
        if (userId == null) return defaultSettings();
        UserToolSettings s = repository.findByUserId(userId).orElse(null);
        if (s != null) return s;
        s = UserToolSettings.builder()
                .userId(userId)
                .memoryEnabled(true)
                .webSearchEnabled(true)
                .createToolEnabled(true)
                .scriptExecEnabled(true)
                .build();
        return repository.save(s);
    }

    @Transactional
    public UserToolSettings update(Long userId, boolean memoryEnabled, boolean webSearchEnabled,
                                   boolean createToolEnabled, boolean scriptExecEnabled) {
        if (userId == null) return defaultSettings();
        ensureAndGet(userId);
        repository.updateFlagsByUserId(userId, memoryEnabled, webSearchEnabled, createToolEnabled, scriptExecEnabled);
        return UserToolSettings.builder()
                .userId(userId)
                .memoryEnabled(memoryEnabled)
                .webSearchEnabled(webSearchEnabled)
                .createToolEnabled(createToolEnabled)
                .scriptExecEnabled(scriptExecEnabled)
                .build();
    }

    /** 按会话解析生效开关（Agent 循环 / 压缩写 Soul 用）。 */
    @Transactional(readOnly = true)
    public ToolSettingsDto resolveForSession(AgentSession session, Long userId) {
        if (session != null && session.isToolSettingsOverridden()) {
            return ToolSettingsDto.of(
                    on(session.getToolMemoryEnabled()),
                    on(session.getToolWebSearchEnabled()),
                    on(session.getToolCreateToolEnabled()),
                    on(session.getToolScriptExecEnabled()),
                    true
            );
        }
        Long agentId = session != null ? session.getUserAgentId() : null;
        if (agentId != null) {
            return userAgentRepository.findById(agentId)
                    .map(ua -> fromAgent(ua, false))
                    .orElseGet(() -> fromUserLevel(userId));
        }
        return fromUserLevel(userId);
    }

    /** 按 Agent 解析默认开关（无会话覆盖时）。 */
    @Transactional(readOnly = true)
    public ToolSettingsDto resolveForAgent(Long userId, Long userAgentId) {
        if (userAgentId == null) return fromUserLevel(userId);
        return userAgentRepository.findByIdAndUserId(userAgentId, userId)
                .map(ua -> fromAgent(ua, false))
                .orElseGet(() -> fromUserLevel(userId));
    }

    /**
     * 聊天框读取：有 sessionId 用该会话；否则用 userAgentId 对应单聊（若有）或 Agent 默认。
     */
    @Transactional(readOnly = true)
    public ToolSettingsDto resolveForChat(Long userId, String sessionId, Long userAgentId) {
        AgentSession session = findChatSession(userId, sessionId, userAgentId);
        if (session != null) {
            return resolveForSession(session, userId);
        }
        if (userAgentId != null) {
            return resolveForAgent(userId, userAgentId);
        }
        return fromUserLevel(userId);
    }

    /**
     * 聊天框写入：写入会话覆盖（聊天优先）。无会话时按 userAgentId 找/需调用方先 openChat。
     */
    @Transactional
    public ToolSettingsDto updateChatOverride(
            Long userId,
            String sessionId,
            Long userAgentId,
            ToolSettingsDto body
    ) {
        AgentSession session = findChatSession(userId, sessionId, userAgentId);
        if (session == null) {
            throw new IllegalArgumentException("请先打开与该伙伴的会话再调整工具开关");
        }
        if (userId != null && session.getUserId() != null && !userId.equals(session.getUserId())) {
            throw new IllegalArgumentException("无权修改该会话");
        }
        session.setToolSettingsOverridden(true);
        session.setToolMemoryEnabled(body.memoryEnabled());
        session.setToolWebSearchEnabled(body.webSearchEnabled());
        session.setToolCreateToolEnabled(body.createToolEnabled());
        session.setToolScriptExecEnabled(body.scriptExecEnabled());
        agentSessionRepository.save(session);
        return ToolSettingsDto.of(
                body.memoryEnabled(),
                body.webSearchEnabled(),
                body.createToolEnabled(),
                body.scriptExecEnabled(),
                true
        );
    }

    /** Agent「设置与偏好」写入默认，并清除该 Agent 下所有单聊覆盖。 */
    @Transactional
    public ToolSettingsDto updateAgentDefaults(Long userId, Long userAgentId, ToolSettingsDto body) {
        UserAgent ua = userAgentRepository.findByIdAndUserId(userAgentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 不存在"));
        ua.setToolMemoryEnabled(body.memoryEnabled());
        ua.setToolWebSearchEnabled(body.webSearchEnabled());
        ua.setToolCreateToolEnabled(body.createToolEnabled());
        ua.setToolScriptExecEnabled(body.scriptExecEnabled());
        userAgentRepository.save(ua);
        agentSessionRepository.clearToolSettingsOverrides(ua.getId());
        return fromAgent(ua, false);
    }

    @Transactional(readOnly = true)
    public boolean isMemoryEnabled(Long userId, Long userAgentId) {
        return resolveForAgent(userId, userAgentId).memoryEnabled();
    }

    private AgentSession findChatSession(Long userId, String sessionId, Long userAgentId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return agentSessionRepository.findBySessionId(sessionId).orElse(null);
        }
        if (userId != null && userAgentId != null) {
            List<AgentSession> list = agentSessionRepository
                    .findByUserIdAndUserAgentIdAndHiddenFalseOrderByLastUserMessageAtDescUpdateTimeDesc(
                            userId, userAgentId, PageRequest.of(0, 1));
            return list.isEmpty() ? null : list.get(0);
        }
        return null;
    }

    private ToolSettingsDto fromAgent(UserAgent ua, boolean overridden) {
        return ToolSettingsDto.of(
                on(ua.getToolMemoryEnabled()),
                on(ua.getToolWebSearchEnabled()),
                on(ua.getToolCreateToolEnabled()),
                on(ua.getToolScriptExecEnabled()),
                overridden ? Boolean.TRUE : Boolean.FALSE
        );
    }

    private ToolSettingsDto fromUserLevel(Long userId) {
        return ToolSettingsDto.from(getOrCreate(userId));
    }

    private static boolean on(Boolean b) {
        return b == null || Boolean.TRUE.equals(b);
    }

    private static UserToolSettings defaultSettings() {
        return UserToolSettings.builder()
                .userId(null)
                .memoryEnabled(true)
                .webSearchEnabled(true)
                .createToolEnabled(true)
                .scriptExecEnabled(true)
                .build();
    }
}
